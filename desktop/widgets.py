import os
from dataclasses import dataclass
from typing import Optional

from PySide6.QtWidgets import (
    QApplication, QWidget, QFrame, QVBoxLayout, QHBoxLayout, QGridLayout, QLabel,
    QComboBox, QPushButton, QSpinBox, QLineEdit, QMessageBox, QDialog, QSpacerItem, QSizePolicy, QStyle, QStyleOption
)
from PySide6.QtCore import Qt, Signal, QMimeData, QPoint, QRectF
from PySide6.QtGui import QPixmap, QDrag, QPainter, QColor, QFontMetrics, QFont, QPainterPath

from models import (Character, DEFAULT_TALENTS, TALENT_MAX, MAX_LEVEL, RELIC_SLOTS, SUBSTATS, RELIC_RARITIES,
                    MAX_RELIC_LEVEL, active_set_bonuses, tooltip_html)
from manager import StarRailManager, DatabaseError
from dialogs import LightConeSearchDialog
from styles import element_badge_style, set_selected, tint_style, ELEMENT_COLORS, ACCENT


# --- Hilfsfunktionen ---

def combo_value(combo: QComboBox) -> str:
    """Liefert den Wert einer ComboBox: die Item-Daten falls gesetzt (Platzhalter -> ""), sonst den Text."""
    data = combo.currentData()
    return data if data is not None else combo.currentText()


def select_value(combo: QComboBox, value: str):
    """Wählt den Eintrag mit passendem Wert (Daten oder Text); sonst den ersten Eintrag."""
    idx = combo.findData(value)
    if idx < 0:
        idx = combo.findText(value)
    combo.setCurrentIndex(max(idx, 0))


def show_db_error(parent: QWidget, error: Exception):
    """Zeigt einen Datenbankfehler als Dialog an."""
    QMessageBox.critical(parent, "Datenbankfehler", str(error))


def section_title(text: str) -> QLabel:
    label = QLabel(text)
    label.setObjectName("sectionTitle")
    return label


def muted(text: str) -> QLabel:
    return QLabel(f"<span style='color:#7a788e; font-size:10px;'>{text}</span>")


def normalize_build(build: dict, element: Optional[str] = None) -> dict:
    """Bringt ein Build in eine vergleichbare Form.

    Relikt-Slots mit Standardwerten zählen als nicht vorhanden, damit z.B. "nie ausgefüllt"
    und "Detailansicht geöffnet, nichts eingetragen" als gleich erkannt werden.
    """
    defaults = RelicPanel.default_relics()
    relics = {
        slot: values for slot, values in (build.get("relics") or {}).items()
        if not isinstance(values, dict) or values != defaults.get(slot)
    }
    return {**build, "relics": relics}


# --- Kleine Widgets ---

class ClickableLineEdit(QLineEdit):
    """Schreibgeschütztes QLineEdit, das bei einem Klick ein Signal auslöst."""
    clicked = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setReadOnly(True)
        self.setCursor(Qt.PointingHandCursor)

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self.clicked.emit()
        super().mousePressEvent(event)


class ChoiceButtonRow(QHBoxLayout):
    """Reihe von Auswahl-Buttons (z.B. C0–C6 oder Veredelung 1–5), genau einer ist ausgewählt."""

    def __init__(self, label: str, values: range, fmt=str, width: int = 24, selected: int = 0):
        super().__init__()
        self.setSpacing(3)
        title = QLabel(f"<b>{label}</b>")
        title.setObjectName("choiceLabel")
        self.addWidget(title)

        self.value = selected
        self.buttons: dict[int, QPushButton] = {}
        for val in values:
            btn = QPushButton(fmt(val))
            btn.setObjectName("choiceButton")
            btn.setFixedSize(width, 20)
            btn.setCursor(Qt.PointingHandCursor)
            btn.clicked.connect(lambda _, v=val: self.select(v))
            self.buttons[val] = btn
            self.addWidget(btn)
        self.addStretch()
        self.select(selected)

    def select(self, value: int):
        self.value = value
        for val, btn in self.buttons.items():
            set_selected(btn, val == value)


class FrostedMixin:
    """Milchglas-Effekt wie `backdrop-blur` in der Web-Version: Hinter dem (halbtransparenten) Widget erscheint der
    verwaschene Teil des Hintergrundbilds. Das Hintergrund-Widget (`frost_pixmap()`) liegt weiter oben im Fensterbaum."""

    FROST_RADIUS = 10

    def paintEvent(self, event):
        painter = QPainter(self)
        background = self.parentWidget()
        while background is not None and not hasattr(background, "frost_pixmap"):
            background = background.parentWidget()
        if background is not None:
            frost = background.frost_pixmap()
            if not frost.isNull():
                origin = self.mapTo(background, QPoint(0, 0))
                path = QPainterPath()
                path.addRoundedRect(QRectF(self.rect()), self.FROST_RADIUS, self.FROST_RADIUS)
                painter.setClipPath(path)
                painter.drawPixmap(0, 0, frost, origin.x(), origin.y(), self.width(), self.height())
                painter.setClipping(False)
        # Hintergrund und Rahmen aus dem Stylesheet (inkl. :hover) darüber zeichnen
        option = QStyleOption()
        option.initFrom(self)
        self.style().drawPrimitive(QStyle.PE_Widget, option, painter, self)


class AddCharacterSlotWidget(FrostedMixin, QFrame):
    FROST_RADIUS = 12

    """Platzhalter-Karte zum Hinzufügen eines neuen Charakters."""
    clicked = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setCursor(Qt.PointingHandCursor)

        layout = QVBoxLayout(self)
        layout.setAlignment(Qt.AlignCenter)

        plus_icon = QLabel("+")
        plus_icon.setObjectName("addSlotPlus")
        plus_icon.setAlignment(Qt.AlignCenter)

        text_label = QLabel("Charakter hinzufügen")
        text_label.setObjectName("addSlotText")
        text_label.setAlignment(Qt.AlignCenter)

        layout.addWidget(plus_icon)
        layout.addWidget(text_label)

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self.clicked.emit()


TEAM_SIZE = 4
TEAM_MIME = "application/x-star-rail-team-summary"   # Nutzdaten: der Teamname


class TeamSummaryCard(FrostedMixin, QFrame):
    """Karte in der Übersicht aller Teams: Teamname und die Icons der Charaktere. Ein Klick öffnet das Team."""
    clicked = Signal(str)
    move_requested = Signal(str, int)      # Teamname, Richtung (-1 = nach vorn, +1 = nach hinten)
    team_dropped = Signal(str, str)        # gezogenes Team, Team, auf dem es abgelegt wurde

    def __init__(self, name: str, members: list[Character], parent=None, can_move_back=False, can_move_forward=False, levels: Optional[dict] = None):
        super().__init__(parent)
        self.team_name = name
        self._press_pos: Optional[QPoint] = None
        self.setObjectName("teamCard")
        self.setCursor(Qt.PointingHandCursor)
        self.setToolTip("Team öffnen")
        self.setAcceptDrops(True)

        layout = QVBoxLayout(self)
        head = QHBoxLayout()
        title = QLabel(name)
        title.setObjectName("teamCardTitle")
        head.addWidget(title, stretch=1)
        for text, direction, enabled, tip in (("‹", -1, can_move_back, "Nach vorn"), ("›", 1, can_move_forward, "Nach hinten")):
            button = QPushButton(text)
            button.setObjectName("teamMoveButton")
            button.setFixedSize(24, 24)
            button.setToolTip(tip)
            button.setEnabled(enabled)
            button.setCursor(Qt.PointingHandCursor)
            button.clicked.connect(lambda _=False, d=direction: self.move_requested.emit(self.team_name, d))
            head.addWidget(button)
        layout.addLayout(head)

        # Vier gleich breite Plätze füllen die Kartenbreite; die Icons sind quadratisch und wachsen mit der Karte
        self._icons: list[tuple[QLabel, Optional[QPixmap]]] = []
        self._names: list[tuple[QLabel, str]] = []
        row = QGridLayout()
        row.setSpacing(10)
        for i in range(TEAM_SIZE):
            row.setColumnStretch(i, 1)
        for i, char in enumerate(members[:TEAM_SIZE]):
            icon = QLabel()
            icon.setAlignment(Qt.AlignCenter)
            border = "#e6c37a" if char.rarity == 5 else "#a561c9"
            # Transparenter Hintergrund in der Farbe des Elements
            color = QColor(ELEMENT_COLORS.get(char.element, "#a99bff"))
            background = f"rgba({color.red()}, {color.green()}, {color.blue()}, 90)"
            icon.setStyleSheet(f"border: 2px solid {border}; border-radius: 8px; background-color: {background};")
            # Charakterlevel klein oben links im Bild
            level_label = QLabel(str((levels or {}).get(char.name, MAX_LEVEL)), icon)
            level_label.setStyleSheet("color: #ffffff; font-size: 10px; font-weight: bold; background: transparent; border: none;")
            level_label.adjustSize()
            level_label.move(6, 3)
            pixmap = QPixmap(char.icon_path) if char.icon_path and os.path.exists(char.icon_path) else None
            self._icons.append((icon, pixmap))
            row.addWidget(icon, 0, i, Qt.AlignHCenter)
            label = QLabel(char.name)
            label.setObjectName("teamCardMember")
            label.setAlignment(Qt.AlignCenter)
            label.setMinimumWidth(10)
            label.setToolTip(char.name)
            self._names.append((label, char.name))
            row.addWidget(label, 1, i)
        if not members:
            row.addWidget(muted("Keine Charaktere"), 0, 0, 1, TEAM_SIZE)
        layout.addLayout(row)
        layout.addStretch(1)      # Inhalt oben, falls die Karte höher ist (gleiche Zeilenhöhe wie die Nachbarkarte)
        # Die Breite bestimmt allein die Übersicht (gleich breite Spalten); die Icons richten sich danach
        self.setMinimumWidth(0)
        self.setSizePolicy(QSizePolicy.Ignored, QSizePolicy.Preferred)
        self._fit_icons()

    def _fit_icons(self):
        """Icongröße an die Kartenbreite anpassen (vier Plätze, quadratisch)."""
        layout = self.layout()
        margins = layout.contentsMargins()
        spacing = 10
        side = max(48, (self.width() - margins.left() - margins.right() - spacing * (TEAM_SIZE - 1)) // TEAM_SIZE)
        for icon, pixmap in self._icons:
            icon.setFixedSize(side, side)
            if pixmap is not None:
                icon.setPixmap(pixmap.scaled(side - 4, side - 4, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        for label, name in self._names:
            label.setText(label.fontMetrics().elidedText(name, Qt.ElideRight, side))

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._fit_icons()

    # --- Öffnen per Klick, Verschieben per Ziehen ---

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self._press_pos = event.position().toPoint()

    def mouseMoveEvent(self, event):
        if (self._press_pos is not None and event.buttons() & Qt.LeftButton
                and (event.position().toPoint() - self._press_pos).manhattanLength() >= QApplication.startDragDistance()):
            self._press_pos = None
            self.start_drag()

    def mouseReleaseEvent(self, event):
        # Ein Klick (ohne Ziehen) öffnet das Team
        if event.button() == Qt.LeftButton and self._press_pos is not None:
            self._press_pos = None
            if self.rect().contains(event.position().toPoint()):
                self.clicked.emit(self.team_name)

    def start_drag(self):
        mime = QMimeData()
        mime.setData(TEAM_MIME, self.team_name.encode("utf-8"))
        drag = QDrag(self)
        drag.setMimeData(mime)
        preview = self.grab()
        drag.setPixmap(preview)
        drag.setHotSpot(QPoint(preview.width() // 2, 20))
        drag.exec(Qt.MoveAction)

    @staticmethod
    def _dragged_name(event) -> Optional[str]:
        mime = event.mimeData()
        if not mime.hasFormat(TEAM_MIME):
            return None
        return bytes(mime.data(TEAM_MIME).data()).decode("utf-8")

    def dragEnterEvent(self, event):
        name = self._dragged_name(event)
        if name is None:
            event.ignore()
            return
        event.acceptProposedAction()
        if name != self.team_name:
            set_selected(self, True)

    def dragLeaveEvent(self, event):
        set_selected(self, False)

    def dropEvent(self, event):
        set_selected(self, False)
        name = self._dragged_name(event)
        if name is None:
            event.ignore()
            return
        event.acceptProposedAction()
        if name != self.team_name:
            self.team_dropped.emit(name, self.team_name)


# --- Charakterkarte ---

# MIME-Typ beim Ziehen einer Karte; die Nutzdaten sind der Charaktername (innerhalb eines Teams eindeutig)
CARD_MIME = "application/x-star-rail-team-card"
CARD_HEADER_HEIGHT = 140


class CardDropArea(QWidget):
    """Übersicht der Teamkarten: nimmt eine gezogene Karte an und meldet ihre neue Position.

    Die Zielposition ergibt sich aus der Zahl der anderen Karten, deren Mitte links vom Loslassen liegt.
    Beim Ziehen zeigt eine Linie, wo die Karte eingefügt wird.
    """
    card_dropped = Signal(str, int)   # Charaktername, neuer Index

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAcceptDrops(True)
        self._indicator_x: Optional[int] = None

    def _other_cards(self, name: str) -> list:
        """Die Karten im Layout (ohne die gezogene). Nur das Layout zählt: entfernte Karten, die noch auf
        deleteLater() warten, sind zwar noch Kindobjekte, aber nicht mehr Teil des Teams."""
        layout = self.layout()
        widgets = (layout.itemAt(i).widget() for i in range(layout.count())) if layout else ()
        return [w for w in widgets if isinstance(w, CharacterCardWidget) and w.character.name != name]

    def target_index(self, name: str, x: int) -> int:
        return sum(1 for c in self._other_cards(name) if c.geometry().center().x() < x)

    def _indicator_for(self, name: str, x: int) -> Optional[int]:
        cards = sorted(self._other_cards(name), key=lambda c: c.geometry().x())
        if not cards:
            return None
        index = self.target_index(name, x)
        if index < len(cards):
            return cards[index].geometry().left() - 6
        return cards[-1].geometry().right() + 6

    @staticmethod
    def _name_of(event) -> Optional[str]:
        mime = event.mimeData()
        if not mime.hasFormat(CARD_MIME):
            return None
        return bytes(mime.data(CARD_MIME).data()).decode("utf-8")

    def dragEnterEvent(self, event):
        if self._name_of(event) is not None:
            event.acceptProposedAction()
        else:
            event.ignore()

    def dragMoveEvent(self, event):
        name = self._name_of(event)
        if name is None:
            event.ignore()
            return
        event.acceptProposedAction()
        self._indicator_x = self._indicator_for(name, event.position().toPoint().x())
        self.update()

    def dragLeaveEvent(self, event):
        self._indicator_x = None
        self.update()

    def dropEvent(self, event):
        name = self._name_of(event)
        self._indicator_x = None
        self.update()
        if name is None:
            event.ignore()
            return
        event.acceptProposedAction()
        self.card_dropped.emit(name, self.target_index(name, event.position().toPoint().x()))

    def paintEvent(self, event):
        super().paintEvent(event)
        if self._indicator_x is not None:
            painter = QPainter(self)
            painter.fillRect(self._indicator_x - 2, 10, 4, self.height() - 20, QColor(ACCENT))


class CharacterCardWidget(QFrame):
    """Charakterkarte mit Level, Eidolons, Lichtkegel, Relikt-Sets und Talenten."""
    close_requested = Signal(object)
    toggle_detail_requested = Signal(object, bool)
    move_requested = Signal(object, int)   # Karte, Richtung (-1 = nach links, +1 = nach rechts)

    def __init__(self, character: Character, manager: StarRailManager, saved_data: dict = None, parent=None):
        super().__init__(parent)
        self._drag_start: Optional[QPoint] = None
        self.character = character
        self.manager = manager
        self.saved_data = saved_data or {}
        self.is_detail_open = False
        # Gesetzt, solange das Relikt-Detailpanel dieser Karte angezeigt wird
        self.relic_panel: Optional["RelicPanel"] = None
        self.init_ui()

    def init_ui(self):
        self.setStyleSheet(tint_style("CharacterCardWidget", self.character.element))
        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(0, 0, 0, 10)
        main_layout.setSpacing(6)

        main_layout.addWidget(self._build_header())

        # Die Blöcke (Info/Eidolon, Lichtkegel, Relikt-Sets, Talente) sind Gruppen. Zusätzlicher Platz wird
        # gleichmäßig auf die Lücken zwischen den Gruppen verteilt.
        info_layout = QVBoxLayout()
        info_layout.setContentsMargins(10, 4, 10, 0)
        info_layout.setSpacing(0)
        builders = (self._build_info, self._build_light_cone, self._build_relics, self._build_talents)
        for i, build in enumerate(builders):
            if i:
                info_layout.addItem(QSpacerItem(0, 10, QSizePolicy.Minimum, QSizePolicy.Expanding))
            group = QVBoxLayout()
            group.setSpacing(10)
            build(group)
            info_layout.addLayout(group)
        main_layout.addLayout(info_layout, stretch=1)

    def _build_header(self) -> QFrame:
        header_frame = QFrame()
        header_frame.setObjectName("cardHeader")
        header_frame.setProperty("rarity", str(self.character.rarity))
        header_frame.setFixedHeight(CARD_HEADER_HEIGHT)
        header_frame.setCursor(Qt.OpenHandCursor)
        header_frame.setToolTip("Ziehen, um die Karte zu verschieben")

        header_layout = QVBoxLayout(header_frame)
        header_layout.setContentsMargins(8, 8, 8, 8)
        header_layout.setSpacing(0)

        top_bar = QHBoxLayout()
        top_bar.setContentsMargins(0, 0, 0, 0)

        rarity_badge = QLabel(f" <b>{self.character.rarity}★</b> ")
        rarity_badge.setObjectName("rarityBadge")

        close_btn = QPushButton("✕")
        close_btn.setObjectName("closeButton")
        close_btn.setFixedSize(22, 22)
        close_btn.setCursor(Qt.PointingHandCursor)
        close_btn.clicked.connect(lambda: self.close_requested.emit(self))

        self.move_left_btn = self._move_button("‹", "Nach links", -1)
        self.move_right_btn = self._move_button("›", "Nach rechts", 1)

        top_bar.addWidget(rarity_badge)
        top_bar.addStretch()
        top_bar.addWidget(self.move_left_btn)
        top_bar.addWidget(self.move_right_btn)
        top_bar.addWidget(close_btn)
        header_layout.addLayout(top_bar)

        img_label = QLabel()
        img_label.setAlignment(Qt.AlignCenter)
        icon_path = self.character.icon_path
        if os.path.exists(icon_path):
            img_label.setPixmap(QPixmap(icon_path).scaledToHeight(85, Qt.SmoothTransformation))
        else:
            img_label.setObjectName("cardImagePlaceholder")
            img_label.setText(f"[ {self.character.name} ]")
        header_layout.addWidget(img_label)
        return header_frame

    def _move_button(self, text: str, tooltip: str, direction: int) -> QPushButton:
        button = QPushButton(text)
        button.setObjectName("moveButton")
        button.setFixedSize(22, 22)
        button.setToolTip(tooltip)
        button.setCursor(Qt.PointingHandCursor)
        button.clicked.connect(lambda: self.move_requested.emit(self, direction))
        return button

    def set_move_enabled(self, left: bool, right: bool):
        """Schaltet die Pfeile an den Rändern des Teams ab."""
        self.move_left_btn.setEnabled(left)
        self.move_right_btn.setEnabled(right)

    # --- Karte per Drag & Drop verschieben (am Kopfbereich anfassen) ---

    def mousePressEvent(self, event):
        in_header = event.position().y() < CARD_HEADER_HEIGHT
        if event.button() == Qt.LeftButton and in_header and not self.is_detail_open:
            self._drag_start = event.position().toPoint()
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event):
        if (self._drag_start is not None and event.buttons() & Qt.LeftButton
                and (event.position().toPoint() - self._drag_start).manhattanLength() >= QApplication.startDragDistance()):
            self.start_drag()
            return
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event):
        self._drag_start = None
        super().mouseReleaseEvent(event)

    def start_drag(self):
        self._drag_start = None
        mime = QMimeData()
        mime.setData(CARD_MIME, self.character.name.encode("utf-8"))
        drag = QDrag(self)
        drag.setMimeData(mime)
        preview = self.grab().scaledToWidth(max(1, self.width() // 2), Qt.SmoothTransformation)
        drag.setPixmap(preview)
        drag.setHotSpot(QPoint(preview.width() // 2, 20))
        drag.exec(Qt.MoveAction)

    def _build_info(self, layout: QVBoxLayout):
        name_elem_box = QHBoxLayout()
        name_label = QLabel(self.character.name)
        name_label.setObjectName("cardName")
        name_label.setMinimumWidth(0)
        name_label.setToolTip(self.character.name)
        self.name_label = name_label
        elem_badge = QLabel(f" {self.character.element} ")
        elem_badge.setStyleSheet(element_badge_style(self.character.element))
        self.elem_badge = elem_badge
        name_elem_box.addWidget(name_label)
        name_elem_box.addStretch()
        name_elem_box.addWidget(elem_badge)
        layout.addLayout(name_elem_box)

        self.char_level_spin = self._level_spin(self.saved_data.get("level", MAX_LEVEL))
        sub_info_box = QHBoxLayout()
        sub_info = QLabel(self.character.path)
        sub_info.setObjectName("cardSubInfo")
        sub_info_box.addWidget(sub_info)
        sub_info_box.addStretch()
        sub_info_box.addWidget(self.char_level_spin)
        layout.addLayout(sub_info_box)

        self.eidolon_row = ChoiceButtonRow(
            "E", range(0, 7), fmt=lambda c: f"{c}", width=26,
            selected=self.saved_data.get("eidolon", 0)
        )
        layout.addLayout(self.eidolon_row)

    def _fit_name(self):
        """Zu lange Namen werden verkleinert, bis sie ganz lesbar sind."""
        label = getattr(self, "name_label", None)
        if label is None or self.elem_badge is None:
            return
        available = self.width() - self.elem_badge.sizeHint().width() - 30
        size = 15
        while size > 8:
            font = QFont()
            font.setBold(True)
            font.setPixelSize(size)
            if QFontMetrics(font).horizontalAdvance(self.character.name) <= available or available <= 0:
                break
            size -= 1
        label.setStyleSheet(f"font-size: {size}px; font-weight: bold;")

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._fit_name()

    def showEvent(self, event):
        super().showEvent(event)
        self._fit_name()

    def _build_light_cone(self, layout: QVBoxLayout):
        header = QHBoxLayout()
        header.setContentsMargins(0, 0, 0, 0)
        header.setSpacing(4)
        header.addWidget(section_title("Lichtkegel"))

        btn_search = QPushButton("🔍")
        btn_search.setObjectName("iconButton")
        btn_search.setFixedSize(18, 18)
        btn_search.setCursor(Qt.PointingHandCursor)
        btn_search.setToolTip("Lichtkegel suchen und filtern")
        btn_search.clicked.connect(self.open_light_cone_dialog)
        header.addWidget(btn_search)
        header.addStretch()
        layout.addLayout(header)

        self.lc_input = ClickableLineEdit()
        self.lc_input.setObjectName("lcInput")
        self.lc_input.setPlaceholderText("Lichtkegel auswählen...")
        self.set_light_cone(self.saved_data.get("light_cone", ""))
        self.lc_input.clicked.connect(self.open_light_cone_dialog)

        self.lc_level_spin = self._level_spin(self.saved_data.get("lc_level", MAX_LEVEL))

        box = QHBoxLayout()
        box.setSpacing(4)
        box.addWidget(self.lc_input)
        box.addWidget(self.lc_level_spin)
        layout.addLayout(box)

        self.superimpose_row = ChoiceButtonRow("S", range(1, 6), selected=self.saved_data.get("lc_superimpose", 1))
        layout.addLayout(self.superimpose_row)
        for btn in self.superimpose_row.buttons.values():
            btn.clicked.connect(self.update_light_cone_tooltip)   # nach ChoiceButtonRow.select, der Wert ist schon gesetzt
        self.update_light_cone_tooltip()

    def _build_relics(self, layout: QVBoxLayout):
        header = QHBoxLayout()
        header.addWidget(section_title("Relikt-Sets"))
        self.toggle_stats_btn = QPushButton("+")
        self.toggle_stats_btn.setObjectName("toggleButton")
        self.toggle_stats_btn.setFixedSize(18, 18)
        self.toggle_stats_btn.setCursor(Qt.PointingHandCursor)
        self.toggle_stats_btn.clicked.connect(self.toggle_detail_view)
        header.addWidget(self.toggle_stats_btn)
        header.addStretch()
        layout.addLayout(header)

        # Die aktiven 2er-/4er-Boni werden aus den sechs Relikt-Slots berechnet
        self.set_summary = QLabel()
        self.set_summary.setObjectName("cardSubInfo")
        self.set_summary.setWordWrap(True)
        self.set_summary.setTextFormat(Qt.RichText)
        self.set_summary.setMinimumHeight(40)
        self.set_summary.setAlignment(Qt.AlignTop | Qt.AlignLeft)
        layout.addWidget(self.set_summary)
        self.update_set_summary(self.saved_data.get("relics") or {})

    def update_set_summary(self, relics: dict):
        """Zeigt die aktiven Set-Boni (z. B. "4× Genius of Brilliant Stars"), mit den Boni als Tooltip."""
        active = active_set_bonuses(relics)
        if not active:
            self.set_summary.setText("<span style='color:#7a788e;'>Keine aktiven Set-Boni</span>")
            self.set_summary.setToolTip("")
            return
        self.set_summary.setText("<br>".join(
            f"<b style='color:#a99bff;'>{n}×</b> {self._escape(name)}" for name, n in active))
        parts = []
        for name, n in active:
            relic_set = self.manager.get_relic_set_by_name(name)
            if relic_set:
                parts.append(relic_set.bonus_tooltip())
        self.set_summary.setToolTip("".join(parts[:1]) if len(parts) == 1 else
                                    tooltip_html("Aktive Set-Boni", [f"{n}× {name}" for name, n in active]))

    @staticmethod
    def _escape(text: str) -> str:
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    def _build_talents(self, layout: QVBoxLayout):
        layout.addWidget(section_title("Talente"))
        talents = self.saved_data.get("talents", {})

        grid = QGridLayout()
        grid.setHorizontalSpacing(4)
        grid.setVerticalSpacing(4)
        self.talent_spins = {}
        labels = (("basic", "Basis:"), ("skill", "Fertigkeit:"), ("ult", "Ultimate:"), ("talent", "Talent:"))
        for i, (key, label) in enumerate(labels):
            spin = QSpinBox()
            spin.setRange(1, TALENT_MAX[key])
            spin.setValue(min(talents.get(key, DEFAULT_TALENTS[key]), TALENT_MAX[key]))
            spin.setFixedWidth(48)
            spin.setToolTip(f"Höchstens Stufe {TALENT_MAX[key]}")
            self.talent_spins[key] = spin
            row, col = divmod(i, 2)
            grid.addWidget(muted(label), row, col * 2)
            grid.addWidget(spin, row, col * 2 + 1)
        grid.setColumnStretch(1, 1)
        grid.setColumnStretch(3, 1)
        layout.addLayout(grid)

    @staticmethod
    def _level_spin(value: int) -> QSpinBox:
        spin = QSpinBox()
        spin.setRange(1, MAX_LEVEL)
        spin.setValue(min(value, MAX_LEVEL))
        spin.setPrefix("Lvl ")
        spin.setFixedWidth(72)
        return spin

    # --- Aktionen ---

    def set_light_cone(self, name: str):
        self.lc_input.setText(name)
        self.lc_input.setCursorPosition(0)
        self.update_light_cone_tooltip()

    def update_light_cone_tooltip(self):
        """Tooltip des Lichtkegels: Fähigkeit bei der aktuellen Überlagerung (sonst nur der Name)."""
        name = self.lc_input.text()
        light_cone = self.manager.get_light_cone_by_name(name)
        row = getattr(self, "superimpose_row", None)
        superimpose = row.value if row else self.saved_data.get("lc_superimpose", 1)
        self.lc_input.setToolTip((light_cone.passive_tooltip(superimpose) if light_cone else "") or name)

    def open_light_cone_dialog(self):
        dialog = LightConeSearchDialog(self.manager.available_light_cones, self.character.path, parent=self)
        if dialog.exec() == QDialog.Accepted and dialog.selected:
            self.set_light_cone(dialog.selected.name)

    def toggle_detail_view(self):
        self.is_detail_open = not self.is_detail_open
        self.toggle_stats_btn.setText("-" if self.is_detail_open else "+")
        self.toggle_detail_requested.emit(self, self.is_detail_open)

    def get_card_data(self) -> dict:
        # Ist das Detailpanel offen, werden dessen aktuelle Werte übernommen
        if self.relic_panel is not None:
            try:
                self.saved_data["relics"] = self.relic_panel.get_relics()
            except RuntimeError:
                # Panel-Widgets wurden bereits gelöscht -> gespeicherte Werte nicht überschreiben
                self.relic_panel = None

        return {
            "name": self.character.name,
            "level": self.char_level_spin.value(),
            "eidolon": self.eidolon_row.value,
            "light_cone": self.lc_input.text(),
            "lc_level": self.lc_level_spin.value(),
            "lc_superimpose": self.superimpose_row.value,
            "relics": self.saved_data.get("relics", {}),
            "talents": {key: spin.value() for key, spin in self.talent_spins.items()},
        }


# --- Relikt-Detailpanel ---

@dataclass
class RelicSlotWidgets:
    relic_set: QComboBox
    rarity: QComboBox
    level: QSpinBox
    main: QComboBox
    main_val: QLineEdit
    subs: list[tuple[QComboBox, QLineEdit]]


class RelicPanel(QFrame):
    """Panel mit Einzelwerten (Set, Level, Haupt- und Substats) der sechs Relikte einer Karte."""

    @classmethod
    def default_relics(cls) -> dict:
        """Die Werte, die ein unbearbeitetes Panel liefert (siehe set_relics({}) / get_relics())."""
        return {
            slot_name: {
                "set": "", "rarity": RELIC_RARITIES[0], "level": MAX_RELIC_LEVEL,
                "main": mains[0], "main_value": "",
                "substats": [{"stat": "", "val": ""} for _ in range(4)],
            }
            for slot_name, _, mains in RELIC_SLOTS
        }

    def __init__(self, card: CharacterCardWidget, manager: StarRailManager, parent=None):
        super().__init__(parent)
        self.setObjectName("relicPanel")
        element = card.character.element
        self.setStyleSheet(
            tint_style("QFrame#relicPanel", element)
            + tint_style("QFrame#relicSlot", element, darken=0.15, alpha=0.85, radius=8)
        )
        self.card = card
        self.manager = manager
        self.slots: dict[str, RelicSlotWidgets] = {}

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(8)

        title = QLabel("<b>Relikt-Einzelwerte, Level & Substats</b>")
        title.setObjectName("panelTitle")
        layout.addWidget(title)

        grid = QGridLayout()
        grid.setSpacing(8)
        for idx, (slot_name, kind, mains) in enumerate(RELIC_SLOTS):
            grid.addWidget(self._build_slot(slot_name, kind, mains), idx // 2, idx % 2)
        for row in range(3):
            grid.setRowStretch(row, 1)
        layout.addLayout(grid, stretch=1)

        buttons = QHBoxLayout()
        buttons.setSpacing(8)
        buttons.addStretch()
        for text, tooltip, handler, primary in (
            ("Zurücksetzen", "Leert alle eingegebenen Relikt-Einzelwerte dieser Karte", self.reset, False),
            ("Neu laden", "Lädt die gespeicherten Reliktwerte aus der SQLite-Datenbank neu", self.reload, False),
            ("Speichern", "Speichert das Build dieses Charakters zentral in der SQLite-Datenbank", self.save, True),
        ):
            btn = QPushButton(text)
            btn.setToolTip(tooltip)
            if primary:
                btn.setObjectName("primaryButton")
            btn.clicked.connect(handler)
            buttons.addWidget(btn)
        layout.addLayout(buttons)

        self.set_relics(card.saved_data.get("relics", {}))

    def _build_slot(self, slot_name: str, kind: str, main_stats: list[str]) -> QFrame:
        box = QFrame()
        box.setObjectName("relicSlot")
        box_layout = QVBoxLayout(box)
        box_layout.setContentsMargins(6, 6, 6, 6)
        box_layout.setSpacing(0)

        title = QLabel(slot_name)
        title.setObjectName("slotTitle")
        level = QSpinBox()
        level.setRange(0, MAX_RELIC_LEVEL)
        level.setPrefix("+")
        level.setFixedWidth(50)
        rarity = QComboBox()
        for r in RELIC_RARITIES:
            rarity.addItem(f"{r}★", r)
        rarity.setFixedWidth(52)

        head = QHBoxLayout()
        head.addWidget(title)
        head.addStretch()
        head.addWidget(muted("Lvl:"))
        head.addWidget(level)
        head.addWidget(rarity)
        box_layout.addLayout(head)

        # Set: Relikt-Sets für Kopf bis Füße, Planar-Ornamente für Planarsphäre und Verbindungsseil
        relic_set = QComboBox()
        relic_set.view().setMinimumWidth(320)
        relic_set.addItem("— Kein Relikt-Set —" if kind == "Relikt" else "— Kein Ornament —", "")
        for item in self.manager.relic_sets_of_kind(kind):
            relic_set.addItem(item.name, item.name)
            relic_set.setItemData(relic_set.count() - 1, item.bonus_tooltip(), Qt.ToolTipRole)
        relic_set.currentIndexChanged.connect(lambda _=0, c=relic_set: self._on_set_changed(c))
        box_layout.addWidget(relic_set)

        # Zusätzliche Höhe wird gleichmäßig auf die Abschnitte des Slots verteilt
        box_layout.addItem(QSpacerItem(0, 4, QSizePolicy.Minimum, QSizePolicy.Expanding))

        main = QComboBox()
        main.addItems(main_stats)
        main_val = self._value_input()
        main_box = QHBoxLayout()
        main_box.addWidget(main)
        main_box.addWidget(main_val)
        box_layout.addLayout(main_box)

        box_layout.addItem(QSpacerItem(0, 4, QSizePolicy.Minimum, QSizePolicy.Expanding))

        subs = []
        sub_grid = QGridLayout()
        sub_grid.setSpacing(3)
        for i in range(4):
            cb = QComboBox()
            cb.addItem("— Keiner —", "")
            for opt in SUBSTATS:
                cb.addItem(opt, opt)
            val = self._value_input()
            sub_box = QHBoxLayout()
            sub_box.setSpacing(2)
            sub_box.addWidget(cb)
            sub_box.addWidget(val)
            sub_grid.addLayout(sub_box, i // 2, i % 2)
            subs.append((cb, val))
        box_layout.addLayout(sub_grid)

        self.slots[slot_name] = RelicSlotWidgets(relic_set, rarity, level, main, main_val, subs)
        return box

    @staticmethod
    def _value_input() -> QLineEdit:
        edit = QLineEdit()
        edit.setPlaceholderText("Wert")
        edit.setFixedWidth(50)
        return edit

    def _on_set_changed(self, combo: QComboBox):
        """Tooltip der Auswahl aktualisieren und die Set-Zusammenfassung auf der Karte nachziehen."""
        relic_set = self.manager.get_relic_set_by_name(combo_value(combo))
        combo.setToolTip(relic_set.bonus_tooltip() if relic_set else "")
        if hasattr(self, "card") and len(self.slots) == len(RELIC_SLOTS):
            self.card.update_set_summary(self.get_relics())

    # --- Werte lesen / setzen ---

    def set_relics(self, relics: dict):
        """Überträgt gespeicherte Werte in die Felder; fehlende Werte werden auf Standard gesetzt."""
        relics = relics if isinstance(relics, dict) else {}
        for slot_name, w in self.slots.items():
            saved = relics.get(slot_name)
            if not isinstance(saved, dict):
                saved = {}
            select_value(w.relic_set, saved.get("set", ""))
            select_value(w.rarity, saved.get("rarity", RELIC_RARITIES[0]))
            w.level.setValue(saved.get("level", MAX_RELIC_LEVEL))
            mains = next(m for n, _, m in RELIC_SLOTS if n == slot_name)
            select_value(w.main, saved.get("main") or mains[0])
            w.main_val.setText(str(saved.get("main_value", "")))

            subs = saved.get("substats", [])
            for i, (cb, val) in enumerate(w.subs):
                sub = subs[i] if i < len(subs) and isinstance(subs[i], dict) else {}
                select_value(cb, sub.get("stat", ""))
                val.setText(str(sub.get("val", "")))
        self.card.update_set_summary(self.get_relics())

    def get_relics(self) -> dict:
        return {
            slot_name: {
                "set": combo_value(w.relic_set),
                "rarity": w.rarity.currentData(),
                "level": w.level.value(),
                "main": w.main.currentText(),
                "main_value": w.main_val.text(),
                "substats": [{"stat": combo_value(cb), "val": val.text()} for cb, val in w.subs],
            }
            for slot_name, w in self.slots.items()
        }

    # --- Button-Aktionen ---

    def reset(self):
        self.set_relics({})

    def reload(self):
        try:
            builds = self.manager.load_user_characters()
        except DatabaseError as e:
            show_db_error(self, e)
            return
        self.set_relics(builds.get(self.card.character.name, {}).get("relics", {}))

    def save(self):
        name = self.card.character.name
        try:
            self.manager.save_user_character(name, self.card.get_card_data())
        except DatabaseError as e:
            show_db_error(self, e)
            return
        QMessageBox.information(self, "Gespeichert",
                                f"Das Build für '{name}' wurde zentral in der SQLite-Datenbank gespeichert!")

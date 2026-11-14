import os
from typing import Any, Optional

from PySide6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QComboBox,
    QPushButton, QTableWidget, QTableWidgetItem, QHeaderView, QAbstractItemView
)
from PySide6.QtCore import Qt, QSize, QUrl, Signal
from PySide6.QtGui import QDesktopServices, QIcon, QPixmap

from models import APP_VERSION, IMAGES_DIR, Character, LightCone

PATHS = ["Die Zerstörung", "Die Jagd", "Die Gelehrsamkeit", "Die Harmonie", "Die Nichtigkeit",
         "Die Bewahrung", "Der Überfluss", "Das Gedenken", "Das Hochgefühl"]
ELEMENTS = ["Physisch", "Feuer", "Eis", "Blitz", "Wind", "Quantum", "Imaginär"]


def present_values(items: list, attr: str, preferred_order: list) -> list:
    """Alle in den Daten vorkommenden Werte eines Attributs.

    Bekannte Werte erscheinen in der Reihenfolge von `preferred_order`, unbekannte alphabetisch dahinter.
    """
    present = {getattr(item, attr) for item in items}
    known = [v for v in preferred_order if v in present]
    return known + sorted(present - set(known))


class SearchDialog(QDialog):
    """Basisklasse für Such-/Auswahldialoge mit Textsuche, Filtern und Tabelle.

    Unterklassen definieren `create_filters()`, `matches()` und `row_values()`.
    """
    HEADERS: list[str] = []
    ICON_SIZE = 32

    def __init__(self, items: list, title: str, search_placeholder: str,
                 select_label: str, noun: str, parent=None):
        super().__init__(parent)
        self.items = items
        self.filtered: list = []
        self.selected: Optional[Any] = None
        self.noun = noun

        self.setWindowTitle(title)
        layout = QVBoxLayout(self)

        self.filter_layout = QHBoxLayout()
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText(search_placeholder)
        self.search_input.textChanged.connect(self.apply_filters)
        self.filter_layout.addWidget(self.search_input)
        self.create_filters()
        layout.addLayout(self.filter_layout)

        self.table = QTableWidget()
        self.table.setColumnCount(len(self.HEADERS))
        self.table.setHorizontalHeaderLabels(self.HEADERS)
        self.table.setSelectionBehavior(QAbstractItemView.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.table.setIconSize(QSize(self.ICON_SIZE, self.ICON_SIZE))
        header = self.table.horizontalHeader()
        for col in range(len(self.HEADERS)):
            header.setSectionResizeMode(col, QHeaderView.Stretch if col == 1 else QHeaderView.ResizeToContents)
        self.table.doubleClicked.connect(self.accept_selection)
        layout.addWidget(self.table)

        bottom_layout = QHBoxLayout()
        self.status_label = QLabel()
        self.status_label.setObjectName("statusLabel")
        bottom_layout.addWidget(self.status_label)
        bottom_layout.addStretch()
        btn_select = QPushButton(select_label)
        btn_select.clicked.connect(self.accept_selection)
        bottom_layout.addWidget(btn_select)
        layout.addLayout(bottom_layout)

        self.apply_filters()

    # --- von Unterklassen zu implementieren ---

    def create_filters(self):
        pass

    def matches(self, item) -> bool:
        raise NotImplementedError

    def row_values(self, item) -> tuple[str, list[str]]:
        """Liefert (Icon-Pfad, Spaltentexte ab Spalte 1)."""
        raise NotImplementedError

    def row_tooltip(self, item) -> str:
        """Tooltip für die ganze Zeile (leer = keiner)."""
        return ""

    # --- Hilfsmethoden ---

    def add_filter_combo(self, all_label: str, values: list, fmt=str) -> QComboBox:
        """Fügt einen Filter hinzu. Der erste Eintrag ("Alle …") hat keinen Wert und filtert nicht."""
        combo = QComboBox()
        combo.addItem(all_label, None)
        for value in values:
            combo.addItem(fmt(value), value)
        combo.currentIndexChanged.connect(self.apply_filters)
        self.filter_layout.addWidget(combo)
        return combo

    @staticmethod
    def filter_ok(combo: QComboBox, value) -> bool:
        wanted = combo.currentData()
        return wanted is None or wanted == value

    def search_ok(self, name: str) -> bool:
        text = self.search_input.text().lower().strip()
        return not text or text in name.lower()

    # --- Logik ---

    def apply_filters(self):
        self.filtered = [item for item in self.items if self.matches(item)]
        self.update_table()

    def update_table(self):
        self.table.setRowCount(len(self.filtered))
        last_col = len(self.HEADERS) - 1
        for row, item in enumerate(self.filtered):
            icon_path, texts = self.row_values(item)
            tooltip = self.row_tooltip(item)
            icon_item = QTableWidgetItem()
            if os.path.exists(icon_path):
                icon_item.setIcon(QIcon(icon_path))
            icon_item.setToolTip(tooltip)
            self.table.setItem(row, 0, icon_item)

            for col, text in enumerate(texts, start=1):
                cell = QTableWidgetItem(text)
                cell.setToolTip(tooltip)
                if col == last_col:
                    cell.setTextAlignment(Qt.AlignCenter)
                self.table.setItem(row, col, cell)

        self.status_label.setText(f"{len(self.filtered)} {self.noun} gefunden")

    def accept_selection(self):
        row = self.table.currentRow()
        if 0 <= row < len(self.filtered):
            self.selected = self.filtered[row]
            self.accept()


class LightConeSearchDialog(SearchDialog):
    """Dialog zur Suche und Filterung von Lichtkegeln inklusive Icons."""
    HEADERS = ["", "Name", "Pfad", "Seltenheit"]
    ICON_SIZE = 32

    def __init__(self, light_cones: list[LightCone], path_filter: Optional[str] = None, parent=None):
        self.preset_path = path_filter
        super().__init__(light_cones, "Lichtkegel auswählen", "Lichtkegel suchen...",
                         "Lichtkegel übernehmen", "Lichtkegel", parent)
        self.resize(650, 500)

    def create_filters(self):
        self.path_filter = self.add_filter_combo("Alle Pfade", present_values(self.items, "path", PATHS))
        rarities = sorted({lc.rarity for lc in self.items}, reverse=True)
        self.rarity_filter = self.add_filter_combo("Alle Seltenheiten", rarities, lambda r: f"{r} ★")
        if self.preset_path:
            # Vorauswahl: der Pfad des Charakters (Lichtkegel passen zum Pfad); Signale blockieren, gefiltert wird später
            self.path_filter.blockSignals(True)
            self.path_filter.setCurrentIndex(max(self.path_filter.findData(self.preset_path), 0))
            self.path_filter.blockSignals(False)

    def matches(self, lc: LightCone) -> bool:
        return (self.search_ok(lc.name)
                and self.filter_ok(self.path_filter, lc.path)
                and self.filter_ok(self.rarity_filter, lc.rarity))

    def row_values(self, lc: LightCone):
        return lc.icon_path, [lc.name, lc.path, f"{lc.rarity} ★"]

    def row_tooltip(self, lc: LightCone) -> str:
        return lc.passive_tooltip(1)


class CharacterSearchDialog(SearchDialog):
    """Dialog zur Suche und Filterung von Charakteren nach Pfad, Element und Seltenheit."""
    HEADERS = ["", "Name", "Element", "Pfad", "Seltenheit"]
    ICON_SIZE = 36

    def __init__(self, characters: list[Character], parent=None):
        super().__init__(characters, "Charakter auswählen", "Charakter suchen...",
                         "Charakter auswählen", "Charaktere", parent)
        self.resize(750, 520)

    def create_filters(self):
        self.path_filter = self.add_filter_combo("Alle Pfade", present_values(self.items, "path", PATHS))
        self.element_filter = self.add_filter_combo("Alle Elemente", present_values(self.items, "element", ELEMENTS))
        rarities = sorted({c.rarity for c in self.items}, reverse=True)
        self.rarity_filter = self.add_filter_combo("Alle Seltenheiten", rarities, lambda r: f"{r} ★")

    def matches(self, c: Character) -> bool:
        return (self.search_ok(c.name)
                and self.filter_ok(self.path_filter, c.path)
                and self.filter_ok(self.element_filter, c.element)
                and self.filter_ok(self.rarity_filter, c.rarity))

    def row_values(self, c: Character):
        return c.icon_path, [c.name, c.element, c.path, f"{c.rarity} ★"]


MEDIA_MATRIX_URL = "https://www.media-matrix.de"


class ClickableLabel(QLabel):
    """Label, das auf einen Linksklick reagiert (Zeigerhand beim Darüberfahren)."""
    clicked = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setCursor(Qt.PointingHandCursor)

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.LeftButton and self.rect().contains(event.position().toPoint()):
            self.clicked.emit()
        super().mouseReleaseEvent(event)


class AboutDialog(QDialog):
    """Kleiner „Über“-Dialog: Name, Version, Kurzbeschreibung und der Hinweis zum Fanprojekt."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Über Team & Build Manager")
        layout = QVBoxLayout(self)
        layout.setSpacing(8)

        icon = QLabel("✦")
        icon.setAlignment(Qt.AlignCenter)
        icon.setStyleSheet("font-size: 44px; color: #a99bff;")
        layout.addWidget(icon)

        title = QLabel("Honkai: Star Rail – Team & Build Manager")
        title.setObjectName("aboutTitle")
        title.setAlignment(Qt.AlignCenter)
        title.setStyleSheet("font-size: 16px; font-weight: bold;")
        layout.addWidget(title)

        self.version_label = QLabel(f"Desktop-Version {APP_VERSION}")
        self.version_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.version_label)

        info = QLabel(
            "Teams aus bis zu vier Charakteren und die Builds deiner Charaktere verwalten. "
            "Die Daten liegen lokal in einer SQLite-Datei."
        )
        info.setWordWrap(True)
        info.setAlignment(Qt.AlignCenter)
        layout.addWidget(info)

        disclaimer = QLabel(
            "Inoffizielles Fanprojekt, nicht mit HoYoverse verbunden. Honkai: Star Rail sowie zugehörige "
            "Namen und Bilder sind Eigentum von HoYoverse."
        )
        disclaimer.setWordWrap(True)
        disclaimer.setAlignment(Qt.AlignCenter)
        disclaimer.setStyleSheet("color: #9a97ab; font-size: 11px;")
        layout.addWidget(disclaimer)

        logo_path = os.path.join(IMAGES_DIR, "ui", "mm_logo.png")
        if os.path.exists(logo_path):
            logo = ClickableLabel()
            logo.setObjectName("aboutLogo")
            logo.clicked.connect(lambda: QDesktopServices.openUrl(QUrl(MEDIA_MATRIX_URL)))
            logo.setPixmap(QPixmap(logo_path).scaledToHeight(41, Qt.SmoothTransformation))
            logo.setAlignment(Qt.AlignCenter)
            logo.setToolTip("the media-matrix – www.media-matrix.de")
            layout.addWidget(logo)

        close = QPushButton("Schließen")
        close.clicked.connect(self.accept)
        layout.addWidget(close, alignment=Qt.AlignCenter)
        self.setFixedWidth(380)

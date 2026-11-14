"""Zentrales Qt-Stylesheet der Anwendung.

Wird einmal per QApplication.setStyleSheet() gesetzt. Varianten werden über
objectName (#name) oder dynamische Properties ([selected="true"]) gesteuert.
"""

# Farbpalette
BG_WINDOW = "#0a0b17"
BG_PANEL = "#18192a"
BG_INPUT = "#12121f"
BG_HOVER = "#242640"
BORDER = "#34365a"
BORDER_SOFT = "#2b2d4a"
ACCENT = "#a99bff"
ACCENT_HOVER = "#9486f0"
GOLD = "#e6c37a"
DANGER = "#df4a28"
TEXT = "#ffffff"
TEXT_SOFT = "#e1e1e6"
TEXT_MUTED = "#8a889e"

ELEMENT_COLORS = {
    "Physisch": "#c9c9c9", "Feuer": "#f4663b", "Eis": "#47b8e0", "Blitz": "#d86bff",
    "Wind": "#4ed3a4", "Quantum": "#8c7cf5", "Imaginär": "#f2d441",
}

APP_QSS = f"""
/* ---------- Grundelemente ---------- */
QMainWindow {{ background-color: {BG_WINDOW}; }}
QDialog {{ background-color: {BG_PANEL}; color: {TEXT}; }}
QDialog QLabel {{ color: {TEXT}; font-size: 12px; }}

QToolTip {{
    background-color: {BG_INPUT}; color: {TEXT};
    border: 1px solid {ACCENT}; border-radius: 6px;
    padding: 6px; font-size: 11px;
}}

QLineEdit {{
    background-color: {BG_INPUT}; color: {TEXT};
    border: 1px solid {BORDER}; border-radius: 6px;
    padding: 4px 6px; font-size: 11px;
}}

QComboBox {{
    background-color: {BG_INPUT}; color: {TEXT};
    border: 1px solid {BORDER}; border-radius: 6px;
    padding: 4px 18px 4px 6px; font-size: 11px;
}}
QComboBox QAbstractItemView {{
    background-color: {BG_PANEL}; color: {TEXT};
    selection-background-color: {ACCENT}; selection-color: {BG_INPUT};
    border: 1px solid {BORDER}; outline: none;
}}

QSpinBox {{
    background-color: {BG_INPUT}; color: {ACCENT};
    border: 1px solid {BORDER}; border-radius: 4px;
    padding-left: 2px; padding-right: 14px;
    font-size: 11px; font-weight: bold;
}}
QSpinBox::up-button, QSpinBox::down-button {{ width: 12px; }}

QPushButton {{
    background-color: {ACCENT}; color: {BG_INPUT}; font-weight: bold;
    border: none; border-radius: 6px; padding: 6px 12px; font-size: 12px;
}}
QPushButton:hover {{ background-color: {ACCENT_HOVER}; }}
QPushButton#dangerButton {{ background-color: {DANGER}; color: {TEXT}; }}
/* Farblich invertiert: dunkler Hintergrund, Akzentfarbe für Text und Rahmen */
QPushButton#secondaryButton {{
    background-color: {BG_INPUT}; color: {ACCENT}; border: 1px solid {ACCENT};
}}
QPushButton#secondaryButton:hover {{ background-color: {BG_HOVER}; }}

/* ---------- Übersicht aller Teams ---------- */
QScrollArea#allTeamsScroll, QScrollArea#allTeamsScroll > QWidget > QWidget, QWidget#allTeamsContent {{ background: transparent; }}
QFrame#teamCard {{
    background-color: rgba(26, 24, 37, 100); border: 1px solid {BORDER}; border-radius: 10px;
}}
QFrame#teamCard:hover, QFrame#teamCard[selected="true"] {{ border-color: {ACCENT}; }}
QPushButton#teamMoveButton {{
    background-color: rgba(0, 0, 0, 0.45); color: {ACCENT};
    border: 1px solid {BORDER}; border-radius: 12px; font-size: 16px; font-weight: bold; padding: 0px 0px 2px 0px;
}}
QPushButton#teamMoveButton:hover {{ background-color: {ACCENT}; color: {BG_INPUT}; border-color: {ACCENT}; }}
QPushButton#teamMoveButton:disabled {{ background-color: rgba(0, 0, 0, 0.2); color: {TEXT_MUTED}; border-color: {BORDER_SOFT}; }}
QLabel#teamCardTitle {{ color: {GOLD}; font-size: 15px; font-weight: bold; background: transparent; }}
QLabel#teamCardMember {{ color: {TEXT_SOFT}; font-size: 11px; background: transparent; }}

/* ---------- Suchdialoge ---------- */
QDialog QLineEdit, QDialog QComboBox {{ padding: 5px; font-size: 12px; }}
QDialog QPushButton {{ border-radius: 4px; }}
QLabel#statusLabel {{ color: {TEXT_MUTED}; }}

QTableWidget {{
    background-color: {BG_INPUT}; color: {TEXT};
    gridline-color: {BG_HOVER}; border: 1px solid {BORDER};
    border-radius: 6px; font-size: 12px;
}}
QTableWidget::item {{ padding: 4px; }}
QTableWidget::item:selected {{ background-color: {BORDER}; color: {ACCENT}; }}
QHeaderView::section {{
    background-color: {BG_PANEL}; color: {GOLD};
    padding: 4px; border: none; font-weight: bold;
}}

/* ---------- Leerer Slot ---------- */
AddCharacterSlotWidget {{
    background-color: rgba(26, 24, 37, 0.4);
    border: 2px dashed {BORDER}; border-radius: 12px;
}}
AddCharacterSlotWidget:hover {{
    background-color: rgba(43, 39, 61, 0.6); border-color: {ACCENT};
}}
QLabel#addSlotPlus {{ font-size: 48px; color: {ACCENT}; }}
QLabel#addSlotText {{ font-size: 13px; color: {TEXT_MUTED}; font-weight: bold; }}

/* ---------- Charakterkarte ---------- */
CharacterCardWidget {{
    background-color: {BG_PANEL};
    border: 1px solid {BORDER_SOFT}; border-radius: 12px;
}}
CharacterCardWidget QLabel {{ color: {TEXT_SOFT}; }}
CharacterCardWidget QComboBox {{ color: #d1d1d6; padding: 4px 24px 4px 8px; }}
CharacterCardWidget QComboBox QAbstractItemView {{
    background-color: {BG_INPUT};
    selection-background-color: {BG_HOVER}; selection-color: {ACCENT};
    border: 1px solid {ACCENT}; border-radius: 6px; padding: 4px;
}}

QFrame#cardHeader {{
    border: none; border-top-left-radius: 12px; border-top-right-radius: 12px;
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #593d7a, stop:1 #29243b);
}}
QFrame#cardHeader[rarity="5"] {{
    background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 #8a602a, stop:1 #2d241d);
}}
QFrame#cardHeader QLabel {{ background: transparent; border: none; color: {TEXT}; }}
QLabel#cardImagePlaceholder {{ color: rgba(255,255,255,0.8); font-size: 12px; font-weight: bold; }}
QFrame#cardHeader QLabel#rarityBadge {{
    background: rgba(0,0,0,0.5); border-radius: 4px; padding: 2px 4px; font-size: 10px;
}}

QPushButton#closeButton {{
    background-color: rgba(0, 0, 0, 0.6); color: {TEXT};
    border-radius: 11px; font-size: 12px; padding: 0px;
}}
QPushButton#closeButton:hover {{ background-color: {DANGER}; }}

QPushButton#moveButton {{
    background-color: rgba(0, 0, 0, 0.6); color: {TEXT};
    border-radius: 11px; font-size: 14px; font-weight: bold; padding: 0px;
}}
QPushButton#moveButton:hover {{ background-color: {ACCENT}; color: {BG_INPUT}; }}
QPushButton#moveButton:disabled {{ background-color: rgba(0, 0, 0, 0.3); color: {TEXT_MUTED}; }}

QLabel#cardName {{ font-size: 15px; color: {TEXT}; }}
QLabel#cardSubInfo {{ font-size: 11px; color: #7a788e; }}
QLabel#sectionTitle {{ color: {GOLD}; font-size: 11px; font-weight: bold; margin-top: 4px; }}
QLabel#choiceLabel {{ color: {GOLD}; font-size: 11px; font-weight: bold; margin-right: 2px; }}

QPushButton#iconButton {{
    background-color: {BG_INPUT}; color: {ACCENT};
    border: 1px solid {BORDER}; border-radius: 4px; font-size: 10px; padding: 0px;
}}
QPushButton#iconButton:hover {{ background-color: {BG_HOVER}; border-color: {ACCENT}; }}

QPushButton#toggleButton {{
    background-color: {BORDER_SOFT}; color: {ACCENT};
    border: 1px solid {BORDER}; border-radius: 9px; font-size: 11px; padding: 0px;
}}
QPushButton#toggleButton:hover {{ background-color: {ACCENT}; color: {BG_INPUT}; }}

QLineEdit#lcInput {{ color: #d1d1d6; padding: 4px 8px; }}
QLineEdit#lcInput:hover {{ border-color: {ACCENT}; color: {TEXT}; }}

/* Auswahl-Buttons für Sternbild (C0–C6) und Veredelung (1–5) */
QPushButton#choiceButton {{
    background-color: {BG_INPUT}; color: {TEXT};
    border: 1px solid {BORDER}; border-radius: 4px;
    font-size: 10px; padding: 0px;
}}
QPushButton#choiceButton:hover {{ background-color: {BG_HOVER}; color: {ACCENT}; border-color: {ACCENT}; }}
QPushButton#choiceButton[selected="true"] {{
    background-color: {BORDER_SOFT}; color: {GOLD}; border-color: {GOLD};
}}

/* ---------- Artefakt-Detailpanel ---------- */
QFrame#relicPanel {{
    background-color: {BG_INPUT}; border: 1px solid {BORDER}; border-radius: 12px;
}}
QFrame#relicPanel QLabel {{ color: {GOLD}; font-size: 11px; }}
QFrame#relicPanel QLabel#panelTitle {{ color: {ACCENT}; font-size: 14px; font-weight: bold; }}
QFrame#relicPanel QPushButton {{
    background-color: {BG_HOVER}; color: {ACCENT};
    border: 1px solid {BORDER}; font-size: 11px;
}}
QFrame#relicPanel QPushButton:hover {{ background-color: {BORDER}; border-color: {ACCENT}; }}
QFrame#relicPanel QPushButton#primaryButton {{
    background-color: {ACCENT}; color: {BG_INPUT}; border: none;
}}
QFrame#relicPanel QPushButton#primaryButton:hover {{ background-color: {ACCENT_HOVER}; }}

QFrame#relicSlot {{
    background-color: {BG_PANEL}; border: 1px solid {BORDER_SOFT}; border-radius: 8px;
}}
QFrame#relicSlot QLabel#slotTitle {{ color: {TEXT_SOFT}; font-weight: bold; }}
QFrame#relicSlot QComboBox, QFrame#relicSlot QLineEdit {{ padding: 4px; }}
"""


def element_badge_style(element: str) -> str:
    """Stil für das farbige Element-Abzeichen (Farbe hängt vom Element ab)."""
    color = ELEMENT_COLORS.get(element, ACCENT)
    return (
        f"background-color: rgba(0,0,0,0.3); color: {color};"
        f"border: 1px solid {color}; border-radius: 10px;"
        "padding: 2px 8px; font-size: 11px; font-weight: bold;"
    )


def tint_style(selector: str, element: str, darken: float = 1.0, alpha: float = 0.16, radius: int = 12) -> str:
    """Halbtransparenter, nach Element getönter Hintergrund mit passendem Rand für `selector`.

    `darken` < 1 dunkelt die Hintergrundfarbe ab (der Rand behält die volle Elementfarbe)."""
    color = ELEMENT_COLORS.get(element)
    if not color:
        return ""
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    dr, dg, db = (int(c * darken) for c in (r, g, b))
    return (
        selector + " {"
        f"background-color: rgba({dr},{dg},{db},{alpha});"
        f"border: 1px solid rgba({r},{g},{b},0.45); border-radius: {radius}px;"
        "}"
    )


def set_selected(widget, selected: bool):
    """Setzt die Property `selected` und aktualisiert den Stil des Widgets."""
    widget.setProperty("selected", selected)
    widget.style().unpolish(widget)
    widget.style().polish(widget)

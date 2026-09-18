#!/usr/bin/env python3
"""
AI_32ch.kicad_sch generator
============================

32-channel 4-20 mA analog input module.

Generated files:
    AI_32ch.kicad_sch
    analog_channel.kicad_sch

Requirements:
    Python >= 3.10
    kicad-sch-api >= 0.5.0
    KiCad 8/9 with the standard symbol libraries installed.

Architecture
------------

ROOT:
    ESP32-S3 / HW-678
        |
        +-- SPI -------------------- ADS8681
        |
        +-- MUX_A/B/C -------------- 4 x CD4051B
        |
        +-- MUX2_A/B --------------- 5th CD4051B
        |
        +-- ADC_RST
        +-- ADC_ALARM

    4 x CD4051B:
        MUX1: channels 01..08
        MUX2: channels 09..16
        MUX3: channels 17..24
        MUX4: channels 25..32

    5th CD4051B:
        Y0 <- MUX1
        Y1 <- MUX2
        Y2 <- MUX3
        Y3 <- MUX4

        C = GND
        A/B = MUX2_A / MUX2_B

    5th MUX -> OPA320 voltage follower -> ADS8681

CHILD:
    One 4-20 mA input channel:

        IN+
          |
        FUSE
          |
        R_PROT 24.9R
          |
          +----------+
          |          |
        TVS         R_SHUNT 165R 0.1%
          |          |
        IN-/AGND ---+
          |
        R_FILTER 1k
          |
        MUX_OUT
          |
        hierarchical output

    IN- also has R_PROT 24.9R.

    C_FILTER = 100nF from filtered signal to AGND.

Important:
    The exact HW-678 GPIO assignment is a project-level choice.
    The script uses ESP32-S3-WROOM-1 GPIO names as placeholders.
    Verify these GPIOs against the actual HW-678 board before PCB design.
"""

from __future__ import annotations

from pathlib import Path
import sys

import kicad_sch_api as ksa


# ---------------------------------------------------------------------------
# Project configuration
# ---------------------------------------------------------------------------

PROJECT_NAME = "AI_32ch"

ROOT_FILE = Path("AI_32ch.kicad_sch")
CHANNEL_FILE = Path("analog_channel.kicad_sch")

NUM_CHANNELS = 32


# ---------------------------------------------------------------------------
# ESP32-S3 GPIO assignment
# ---------------------------------------------------------------------------
#
# These are deliberately kept in one table so the actual HW-678 pinout can
# be changed without touching the rest of the generator.
#
# ESP32-S3-WROOM-1 is used as the schematic symbol because the HW-678 board
# itself is not a standard KiCad library symbol.
#
# Change these GPIOs if the HW-678 exposes a different routing.
# ---------------------------------------------------------------------------

ESP_PINS = {
    # SPI
    "SCLK": "GPIO12",
    "MOSI": "GPIO11",
    "MISO": "GPIO13",
    "CS": "GPIO10",
    # First-stage CD4051B address bus
    "MUX_A": "GPIO4",
    "MUX_B": "GPIO5",
    "MUX_C": "GPIO6",
    # Second-stage CD4051B address bus
    "MUX2_A": "GPIO7",
    "MUX2_B": "GPIO8",
    # ADC control
    "ADC_RST": "GPIO9",
    "ADC_ALARM": "GPIO14",
}


# ---------------------------------------------------------------------------
# Utility functions
# ---------------------------------------------------------------------------


def norm_name(value: str) -> str:
    """Normalize a KiCad pin name for matching."""
    return (
        value.strip()
        .upper()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
        .replace("/", "")
    )


def find_pin(component, *names: str):
    """
    Find a component pin by its displayed name.

    The official KiCad symbols occasionally use names such as:
        AIN_P
        AINP
        CONVST/CS
        SDO-0

    Therefore matching is intentionally tolerant.
    """
    wanted = {norm_name(x) for x in names}

    for pin in component.pins:
        pin_name = getattr(pin, "name", "")
        if norm_name(pin_name) in wanted:
            return pin

    # Second pass: substring matching.
    for pin in component.pins:
        pin_name = norm_name(getattr(pin, "name", ""))

        for candidate in wanted:
            if candidate in pin_name or pin_name in candidate:
                return pin

    available = [
        f"{getattr(p, 'number', '?')}={getattr(p, 'name', '?')}" for p in component.pins
    ]

    raise RuntimeError(
        f"Cannot find pin {names!r} on {component.reference} "
        f"({component.lib_id}). Available pins: {available}"
    )


def pin_number(component, *names: str) -> str:
    """Return the KiCad pin number for a named pin."""
    return str(find_pin(component, *names).number)


def pin_position(sch, component, *names: str):
    """Return absolute schematic coordinates of a named pin."""
    number = pin_number(component, *names)
    position = component.get_pin_position(number)

    if position is None:
        raise RuntimeError(
            f"Cannot determine position of {component.reference}.{number}"
        )

    return (position.x, position.y)


def label_pin(sch, component, label: str, *pin_names: str):
    """Place a local label directly on a component pin."""
    pos = pin_position(sch, component, *pin_names)
    sch.add_label(label, position=pos)


def wire_named_pins(
    sch,
    component_a,
    pin_a: tuple[str, ...],
    component_b,
    pin_b: tuple[str, ...],
):
    """Connect two named pins."""
    a = pin_number(component_a, *pin_a)
    b = pin_number(component_b, *pin_b)

    result = sch.add_wire_between_pins(
        component_a.reference,
        a,
        component_b.reference,
        b,
    )

    if result is None:
        raise RuntimeError(
            f"Failed to wire "
            f"{component_a.reference}.{pin_a} -> "
            f"{component_b.reference}.{pin_b}"
        )


def add_net_label(sch, component, label: str, *pin_names: str):
    """
    Attach a net label to a component pin.

    This is useful for buses such as:
        MUX_A
        SPI_SCLK
        +5V
        AGND
    """
    label_pin(sch, component, label, *pin_names)


def add_text(sch, text: str, position: tuple[float, float]):
    """Add a simple explanatory text label."""
    try:
        sch.add_text(text, position=position)
    except TypeError:
        # Compatibility fallback for versions with a different signature.
        sch.texts.add(text, position=position)


# ---------------------------------------------------------------------------
# Child channel sheet
# ---------------------------------------------------------------------------


def create_channel_sheet() -> None:
    """
    Generate the reusable one-channel analog input sheet.

    The same .kicad_sch is instantiated 32 times by the root schematic.
    """

    print("Creating analog_channel.kicad_sch ...")

    sch = ksa.create_schematic(PROJECT_NAME)

    # -----------------------------------------------------------------------
    # Components
    # -----------------------------------------------------------------------

    # Input connector
    j1 = sch.components.add(
        "Connector_Generic:Conn_01x02",
        "J1",
        "IN+ / IN-",
        position=(50, 90),
    )

    # Positive-side PTC/fuse
    f1 = sch.components.add(
        "Device:Fuse",
        "F1",
        "PTC 60mA",
        position=(75, 80),
    )

    # 24.9 ohm input protection resistors
    r1 = sch.components.add(
        "Device:R",
        "R1",
        "24.9R",
        position=(100, 80),
    )

    r2 = sch.components.add(
        "Device:R",
        "R2",
        "24.9R",
        position=(100, 100),
    )

    # TVS across the field loop
    d1 = sch.components.add(
        "Device:D_TVS",
        "D1",
        "TVS3300",
        position=(125, 90),
    )

    # Optional second TVS from positive signal to AGND
    d2 = sch.components.add(
        "Device:D_TVS",
        "D2",
        "TVS3300 to AGND",
        position=(125, 70),
    )

    # Precision shunt
    r3 = sch.components.add(
        "Device:R",
        "R3",
        "165R 0.1%",
        position=(150, 90),
    )

    # RC filter
    r4 = sch.components.add(
        "Device:R",
        "R4",
        "1k",
        position=(180, 80),
    )

    c1 = sch.components.add(
        "Device:C",
        "C1",
        "100nF",
        position=(180, 105),
    )

    # -----------------------------------------------------------------------
    # Local net names
    # -----------------------------------------------------------------------

    # Connector pin 1 = IN+
    # Connector pin 2 = IN-
    j1_p = j1.get_pin("1")
    j1_n = j1.get_pin("2")

    j1_p_pos = j1.get_pin_position("1")
    j1_n_pos = j1.get_pin_position("2")

    sch.add_hierarchical_label("IN+", position=(j1_p_pos.x, j1_p_pos.y), shape="input")

    sch.add_hierarchical_label("IN-", position=(j1_n_pos.x, j1_n_pos.y), shape="input")

    # -----------------------------------------------------------------------
    # Wiring: IN+ path
    # -----------------------------------------------------------------------

    wire_named_pins(
        sch,
        j1,
        ("1",),
        f1,
        ("1",),
    )

    wire_named_pins(
        sch,
        f1,
        ("2",),
        r1,
        ("1",),
    )

    # -----------------------------------------------------------------------
    # Wiring: IN- path
    # -----------------------------------------------------------------------

    wire_named_pins(
        sch,
        j1,
        ("2",),
        r2,
        ("1",),
    )

    # -----------------------------------------------------------------------
    # Protection / shunt
    # -----------------------------------------------------------------------

    # R1 output is positive field node.
    # R2 output is negative field node.

    wire_named_pins(
        sch,
        r1,
        ("2",),
        d1,
        ("1",),
    )

    wire_named_pins(
        sch,
        r2,
        ("2",),
        d1,
        ("2",),
    )

    wire_named_pins(
        sch,
        r1,
        ("2",),
        r3,
        ("1",),
    )

    wire_named_pins(
        sch,
        r2,
        ("2",),
        r3,
        ("2",),
    )

    # Optional TVS from positive line to AGND.
    wire_named_pins(
        sch,
        r1,
        ("2",),
        d2,
        ("1",),
    )

    label_pin(sch, d2, "AGND", "2")

    # -----------------------------------------------------------------------
    # RC filter
    # -----------------------------------------------------------------------

    wire_named_pins(
        sch,
        r1,
        ("2",),
        r4,
        ("1",),
    )

    wire_named_pins(
        sch,
        r4,
        ("2",),
        c1,
        ("1",),
    )

    # C1 -> AGND
    label_pin(sch, c1, "AGND", "2")

    # The filtered output is the second pin of C1 / R4 node.
    out_pos = pin_position(sch, c1, "1")

    sch.add_hierarchical_label(
        "MUX_OUT",
        "output",
        position=out_pos,
    )

    # -----------------------------------------------------------------------
    # Ground/reference label
    # -----------------------------------------------------------------------

    # Make the field return explicit.
    label_pin(sch, r2, "AGND", "2")

    # -----------------------------------------------------------------------
    # Notes
    # -----------------------------------------------------------------------

    add_text(
        sch,
        "ONE 4-20mA INPUT CHANNEL",
        (50, 55),
    )

    add_text(
        sch,
        "RSHUNT = 165R / 0.1% -> 0.66V @ 4mA, 3.30V @ 20mA",
        (50, 60),
    )

    add_text(
        sch,
        "Verify field-return/AGND isolation strategy before PCB.",
        (50, 65),
    )

    # -----------------------------------------------------------------------
    # Save
    # -----------------------------------------------------------------------

    sch.save(str(CHANNEL_FILE))

    print(f"  OK: {CHANNEL_FILE}")


# ---------------------------------------------------------------------------
# Root schematic
# ---------------------------------------------------------------------------


def create_root_sheet() -> None:
    """Generate the main 32-channel schematic."""

    print("Creating AI_32ch.kicad_sch ...")

    sch = ksa.create_schematic(PROJECT_NAME)

    # IMPORTANT:
    # Save the parent UUID before adding hierarchical sheets.
    parent_uuid = sch.uuid

    # -----------------------------------------------------------------------
    # ESP32-S3 module
    # -----------------------------------------------------------------------

    esp = sch.components.add(
        "RF_Module:ESP32-S3-WROOM-1",
        "U1",
        "HW-678 / ESP32-S3",
        position=(245, 75),
        footprint="RF_Module:ESP32-S3-WROOM-1",
    )

    # Power
    add_net_label(sch, esp, "3V3_DIG", "3V3")
    add_net_label(sch, esp, "DGND", "GND")

    # SPI
    add_net_label(sch, esp, "SPI_SCLK", ESP_PINS["SCLK"])
    add_net_label(sch, esp, "SPI_MOSI", ESP_PINS["MOSI"])
    add_net_label(sch, esp, "SPI_MISO", ESP_PINS["MISO"])
    add_net_label(sch, esp, "ADC_CS", ESP_PINS["CS"])

    # MUX address bus
    add_net_label(sch, esp, "MUX_A", ESP_PINS["MUX_A"])
    add_net_label(sch, esp, "MUX_B", ESP_PINS["MUX_B"])
    add_net_label(sch, esp, "MUX_C", ESP_PINS["MUX_C"])

    add_net_label(sch, esp, "MUX2_A", ESP_PINS["MUX2_A"])
    add_net_label(sch, esp, "MUX2_B", ESP_PINS["MUX2_B"])

    # ADC reset / alarm
    add_net_label(sch, esp, "ADC_RST", ESP_PINS["ADC_RST"])
    add_net_label(sch, esp, "ADC_ALARM", ESP_PINS["ADC_ALARM"])

    # -----------------------------------------------------------------------
    # Four first-stage CD4051B multiplexers
    # -----------------------------------------------------------------------

    muxes = []

    for i, position in enumerate(
        [
            (95, 70),
            (95, 130),
            (95, 190),
            (95, 250),
        ],
        start=1,
    ):
        mux = sch.components.add(
            "4xxx:4051",
            f"U{i + 1}",
            "CD4051B",
            position=position,
        )

        muxes.append(mux)

        # Address bus shared by all four muxes.
        add_net_label(sch, mux, "MUX_A", "A")
        add_net_label(sch, mux, "MUX_B", "B")
        add_net_label(sch, mux, "MUX_C", "C")

        # Enable mux.
        label_pin(sch, mux, "GND", "INH")

        # Power.
        label_pin(sch, mux, "3V3_A", "VDD")
        label_pin(sch, mux, "AGND", "VSS")

        # CD4051B VEE is tied to analog ground for single-ended operation.
        label_pin(sch, mux, "AGND", "VEE")

    # -----------------------------------------------------------------------
    # Fifth CD4051B
    # -----------------------------------------------------------------------

    mux5 = sch.components.add(
        "4xxx:4051",
        "U6",
        "CD4051B 4:1 SECOND STAGE",
        position=(145, 160),
    )

    add_net_label(sch, mux5, "MUX2_A", "A")
    add_net_label(sch, mux5, "MUX2_B", "B")

    # Force C=0 -> only Y0..Y3 are used.
    label_pin(sch, mux5, "GND", "C")

    label_pin(sch, mux5, "GND", "INH")
    label_pin(sch, mux5, "3V3_A", "VDD")
    label_pin(sch, mux5, "AGND", "VSS")
    label_pin(sch, mux5, "AGND", "VEE")

    # -----------------------------------------------------------------------
    # OPA320 buffer
    # -----------------------------------------------------------------------

    opa = sch.components.add(
        "Amplifier_Operational:MCP6001R",
        "U7",
        "OPA320",
        position=(185, 160),
    )

    # Single-supply 5V operation.
    label_pin(sch, opa, "5V_A", "V+")
    label_pin(sch, opa, "AGND", "V-")

    # -----------------------------------------------------------------------
    # ADS8681
    # -----------------------------------------------------------------------

    adc = sch.components.add(
        "Analog_ADC:ADS8681RUM",
        "U8",
        "ADS8681",
        position=(225, 160),
    )

    # ADC analog input.
    #
    # Official ADS8681 symbol has:
    #   AIN_P
    #   AIN_GND
    #   RST
    #   SDI
    #   CONVST/CS
    #   SCLK
    #   SDO-0
    #   SDO-1
    #   RVS
    #   AVDD
    #   AVSS / AGND depending on library revision
    #
    label_pin(sch, adc, "ADC_IN", "AIN_P")
    label_pin(sch, adc, "AGND", "AIN_GND")

    # SPI
    label_pin(sch, adc, "SPI_SCLK", "SCLK")
    label_pin(sch, adc, "SPI_MOSI", "SDI")
    label_pin(sch, adc, "SPI_MISO", "SDO-0")
    label_pin(sch, adc, "ADC_CS", "~{CS}")

    # Hardware reset.
    label_pin(sch, adc, "ADC_RST", "RST")

    # SDO-1 can be configured as ALARM.
    label_pin(sch, adc, "ADC_ALARM", "SDO-1")

    # RVS is left available.
    label_pin(sch, adc, "ADC_RVS", "RVS")

    # Supplies.
    label_pin(sch, adc, "5V_A", "AVDD")
    label_pin(sch, adc, "3V3_A", "DVDD")
    label_pin(sch, adc, "AGND", "AGND")
    label_pin(sch, adc, "AGND", "REFGND")
    label_pin(sch, adc, "AGND", "DGND")

    # -----------------------------------------------------------------------
    # ADS8681 reference network
    # -----------------------------------------------------------------------
    #
    # ADS8681 contains an internal precision reference.
    # REF5030 is therefore kept as an optional DNP component.
    #

    ref = sch.components.add(
        "Reference_Voltage:REF5030ID",
        "U9",
        "REF5030 3.0V DNP",
        position=(225, 245),
    )

    # Do not connect it.
    add_text(
        sch,
        "U9 REF5030: OPTIONAL / DNP. ADS8681 internal reference used.",
        (205, 260),
    )

    # -----------------------------------------------------------------------
    # Analog 3.3V LDO
    # -----------------------------------------------------------------------

    ldo = sch.components.add(
        "Regulator_Linear:AMS1117-3.3",
        "U10",
        "3.3V LDO ANALOG",
        position=(175, 245),
    )

    label_pin(sch, ldo, "5V_ISO_ANALOG", "VI")
    label_pin(sch, ldo, "3V3_A", "VO")
    label_pin(sch, ldo, "AGND", "GND")

    # -----------------------------------------------------------------------
    # 24V -> 5V power converter
    # -----------------------------------------------------------------------
    #
    # No universal KiCad symbol exists for an arbitrary industrial DC/DC
    # module, so a generic 4-pin connector symbol is used.
    # Replace with your selected converter's symbol later.
    #

    j2 = sch.components.add(
        "Connector_Generic:Conn_01x04",
        "J2",
        "24V -> 5V DC/DC MODULE",
        position=(45, 255),
    )

    # Input protection.
    d3 = sch.components.add(
        "Device:D_Schottky",
        "D3",
        "Schottky input protection",
        position=(70, 245),
    )

    d4 = sch.components.add(
        "Device:D_TVS",
        "D4",
        "24V TVS",
        position=(70, 270),
    )

    f2 = sch.components.add(
        "Device:Fuse",
        "F2",
        "Input fuse",
        position=(90, 245),
    )

    label_pin(sch, j2, "+24V_IN", "1")
    label_pin(sch, j2, "PGND", "2")

    # Generic converter pin convention:
    #   1 VIN+
    #   2 VIN-
    #   3 VOUT+
    #   4 VOUT-
    label_pin(sch, j2, "+24V_PROTECTED", "1")
    label_pin(sch, j2, "PGND", "2")
    label_pin(sch, j2, "5V_MAIN", "3")
    label_pin(sch, j2, "PGND", "4")

    # -----------------------------------------------------------------------
    # B0505S #1 - analog isolated 5V
    # -----------------------------------------------------------------------

    b1 = sch.components.add(
        "Connector_Generic:Conn_01x04",
        "U11",
        "B0505S #1 - 5V_ISO_ANALOG",
        position=(125, 285),
    )

    label_pin(sch, b1, "5V_MAIN", "1")
    label_pin(sch, b1, "PGND", "2")
    label_pin(sch, b1, "5V_ISO_ANALOG", "3")
    label_pin(sch, b1, "AGND", "4")

    # -----------------------------------------------------------------------
    # B0505S #2 - digital isolated 5V
    # -----------------------------------------------------------------------

    b2 = sch.components.add(
        "Connector_Generic:Conn_01x04",
        "U12",
        "B0505S #2 - 5V_ISO_DIGITAL",
        position=(125, 325),
    )

    label_pin(sch, b2, "5V_MAIN", "1")
    label_pin(sch, b2, "PGND", "2")
    label_pin(sch, b2, "5V_ISO_DIGITAL", "3")
    label_pin(sch, b2, "DGND", "4")

    # -----------------------------------------------------------------------
    # 5V -> ±12V converter
    # -----------------------------------------------------------------------

    u13 = sch.components.add(
        "Connector_Generic:Conn_01x04",
        "U13",
        "5V -> +/-12V DC/DC MODULE",
        position=(165, 325),
    )

    label_pin(sch, u13, "5V_MAIN", "1")
    label_pin(sch, u13, "PGND", "2")
    label_pin(sch, u13, "+12V", "3")
    label_pin(sch, u13, "-12V", "4")

    add_text(
        sch,
        "NOTE: ADS8681 AVDD is ~5V, not +/-12V. "
        "Keep +/-12V only if another analog circuit actually requires it.",
        (145, 345),
    )

    # -----------------------------------------------------------------------
    # Connect mux outputs to 5th mux
    # -----------------------------------------------------------------------
    #
    # First-stage:
    #   U2 -> mux Y
    #   U3 -> mux Y1
    #   U4 -> mux Y2
    #   U5 -> mux Y3
    #
    # The remaining Y4..Y7 of the second-stage mux are intentionally unused.
    #

    for mux, input_name in zip(
        muxes,
        ["X0", "X1", "X2", "X3"],
    ):
        # We cannot connect a mux output to a named input without a wire
        # because these are actual physical nets. The first-stage Z pin is
        # connected to the corresponding Y input of U6.
        wire_named_pins(
            sch,
            mux,
            ("X",),
            mux5,
            (input_name,),
        )

    # -----------------------------------------------------------------------
    # Fifth MUX -> OPA320 voltage follower
    # -----------------------------------------------------------------------

    wire_named_pins(
        sch,
        mux5,
        ("X",),
        opa,
        ("+IN", "+"),
    )

    # OPA follower:
    # OUT -> -IN
    wire_named_pins(
        sch,
        opa,
        ("OUT",),
        opa,
        ("-IN", "-"),
    )

    # OPA output -> ADC
    wire_named_pins(
        sch,
        opa,
        ("OUT",),
        adc,
        ("AIN_P",),
    )

    # -----------------------------------------------------------------------
    # Create 32 repeated channel sheets
    # -----------------------------------------------------------------------

    print("Creating 32 hierarchical channel instances ...")

    sheet_instances = []

    # Four columns x eight rows.
    sheet_w = 42
    sheet_h = 25

    start_x = 20
    start_y = 20

    gap_x = 48
    gap_y = 32

    for channel in range(1, NUM_CHANNELS + 1):
        col = (channel - 1) % 4
        row = (channel - 1) // 4

        x = start_x + col * gap_x
        y = start_y + row * gap_y

        sheet_name = f"CH{channel:02d}"

        sheet_uuid = sch.sheets.add_sheet(
            name=sheet_name,
            filename=str(CHANNEL_FILE),
            position=(x, y),
            size=(sheet_w, sheet_h),
            project_name=PROJECT_NAME,
        )

        # Child interface:
        #
        # MUX_OUT -> right side
        # AGND    -> bottom
        #
        # Inputs remain entirely inside the child sheet.
        sch.sheets.add_sheet_pin(
            sheet_uuid,
            "MUX_OUT",
            "output",
            "right",
            12,
        )

        sch.sheets.add_sheet_pin(
            sheet_uuid,
            "AGND",
            "passive",
            "bottom",
            21,
        )

        sheet_instances.append(
            (
                channel,
                sheet_uuid,
                x,
                y,
                sheet_w,
                sheet_h,
            )
        )

    # -----------------------------------------------------------------------
    # Hierarchical context for the reusable child sheet
    # -----------------------------------------------------------------------
    #
    # kicad-sch-api requires hierarchy context before components are added
    # to a child schematic so that its instance path is correct.
    #
    # The same channel file is subsequently reused by all 32 sheet instances.
    #

    first_sheet_uuid = sheet_instances[0][1]

    # -----------------------------------------------------------------------
    # Save root before creating child
    # -----------------------------------------------------------------------

    sch.save(str(ROOT_FILE))

    # -----------------------------------------------------------------------
    # Create child after root exists.
    # -----------------------------------------------------------------------

    channel = ksa.create_schematic(PROJECT_NAME)
    channel.set_hierarchy_context(parent_uuid, first_sheet_uuid)

    # Re-create the child circuit with proper hierarchy context.
    create_channel_sheet_with_context(channel)

    channel.save(str(CHANNEL_FILE))

    print(f"  OK: {ROOT_FILE}")
    print(f"  OK: {CHANNEL_FILE}")


# ---------------------------------------------------------------------------
# Proper child-sheet creation with hierarchy context
# ---------------------------------------------------------------------------


def create_channel_sheet_with_context(sch) -> None:
    """
    Same circuit as create_channel_sheet(), but operates on an already
    hierarchy-configured schematic.

    This avoids creating two different child files.
    """

    # Input connector
    j1 = sch.components.add(
        "Connector_Generic:Conn_01x02",
        "J1",
        "IN+ / IN-",
        position=(50, 90),
    )

    f1 = sch.components.add(
        "Device:Fuse",
        "F1",
        "PTC 60mA",
        position=(75, 80),
    )

    r1 = sch.components.add(
        "Device:R",
        "R1",
        "24.9R",
        position=(100, 80),
    )

    r2 = sch.components.add(
        "Device:R",
        "R2",
        "24.9R",
        position=(100, 100),
    )

    d1 = sch.components.add(
        "Device:D_TVS",
        "D1",
        "TVS3300",
        position=(125, 90),
    )

    d2 = sch.components.add(
        "Device:D_TVS",
        "D2",
        "TVS3300 to AGND",
        position=(125, 70),
    )

    r3 = sch.components.add(
        "Device:R",
        "R3",
        "165R 0.1%",
        position=(150, 90),
    )

    r4 = sch.components.add(
        "Device:R",
        "R4",
        "1k",
        position=(180, 80),
    )

    c1 = sch.components.add(
        "Device:C",
        "C1",
        "100nF",
        position=(180, 105),
    )

    # IN+ hierarchical label
    p = j1.get_pin_position("1")
    sch.add_hierarchical_label("IN+", position=(p.x, p.y), shape="input")

    # IN- hierarchical label
    p = j1.get_pin_position("2")
    sch.add_hierarchical_label("IN-", position=(p.x, p.y), shape="input")

    # IN+ path
    wire_named_pins(sch, j1, ("1",), f1, ("1",))

    wire_named_pins(sch, f1, ("2",), r1, ("1",))

    # IN- path
    wire_named_pins(sch, j1, ("2",), r2, ("1",))

    # Protection
    wire_named_pins(sch, r1, ("2",), d1, ("1",))

    wire_named_pins(sch, r2, ("2",), d1, ("2",))

    wire_named_pins(sch, r1, ("2",), r3, ("1",))

    wire_named_pins(sch, r2, ("2",), r3, ("2",))

    # TVS to AGND
    wire_named_pins(sch, r1, ("2",), d2, ("1",))

    label_pin(sch, d2, "AGND", "2")

    # RC filter
    wire_named_pins(sch, r1, ("2",), r4, ("1",))

    wire_named_pins(sch, r4, ("2",), c1, ("1",))

    label_pin(sch, c1, "AGND", "2")
    label_pin(sch, r2, "AGND", "2")

    # MUX output hierarchical label
    p = c1.get_pin_position("1")
    sch.add_hierarchical_label("MUX_OUT", position=(p.x, p.y), shape="output")

    # AGND hierarchical label.
    #
    # It is attached to the same AGND net used by C1.
    p = c1.get_pin_position("2")
    sch.add_hierarchical_label("AGND", position=(p.x, p.y), shape="passive")

    add_text(
        sch,
        "4-20mA CHANNEL",
        (50, 55),
    )

    add_text(
        sch,
        "RSHUNT 165R / 0.1%",
        (50, 60),
    )

    add_text(
        sch,
        "4mA = 0.66V, 20mA = 3.30V",
        (50, 65),
    )


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------


def main() -> int:
    print("=" * 70)
    print("32-channel 4-20mA AI / KiCad schematic generator")
    print("=" * 70)

    try:
        # First create the root because the child hierarchy context needs
        # the UUID of the parent and the UUID of at least one sheet instance.
        create_root_sheet()

    except Exception as exc:
        print()
        print("ERROR while generating schematic:")
        print(f"  {type(exc).__name__}: {exc}")
        print()
        print(
            "Check that the KiCad standard symbol libraries are installed "
            "and that the selected symbols exist in your KiCad version."
        )
        return 1

    print()
    print("=" * 70)
    print("Generation complete.")
    print("=" * 70)
    print()
    print("Generated:")
    print(f"  {ROOT_FILE}")
    print(f"  {CHANNEL_FILE}")
    print()
    print("Open AI_32ch.kicad_sch in KiCad.")
    print()
    print("IMPORTANT:")
    print("  1. Run ERC.")
    print("  2. Verify HW-678 GPIO mapping.")
    print("  3. Replace generic DC/DC symbols with actual modules.")
    print("  4. Verify the field-return / AGND isolation concept.")
    print("  5. Verify ADS8681 supply and reference implementation.")
    print()

    return 0


if __name__ == "__main__":
    sys.exit(main())

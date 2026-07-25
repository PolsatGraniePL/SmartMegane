package pl.polsatgranie.smartmegane.domain.signal

/**
 * The single CAN-to-signal mapping boundary.
 *
 * "Confirmed" entries below come from captures made in the target Megane II.
 * "Candidate" entries are corroborated by the Clio III CAN-V reverse engineering
 * at https://x0r.fr/blog/39 and remain deliberately isolated here for validation.
 *
 * Byte indices are zero-based. All multi-byte Renault fields mapped here are
 * big-endian; this is independent from the little-endian CAN ID in the Waveshare
 * USB envelope.
 */
object SignalDefinitions {
    private const val ID_STEERING = 0x0C2
    private const val ID_DRIVER_TORQUE = 0x161
    private const val ID_ENGINE = 0x181
    private const val ID_RPM_SECONDARY = 0x1F9
    private const val ID_REVERSE = 0x215
    private const val ID_SPEED = 0x354
    private const val ID_WIPERS = 0x35D
    private const val ID_COOLANT = 0x551
    private const val ID_ODOMETER_PARKING_BRAKE = 0x5C5
    private const val ID_ODOMETER_AGE = 0x5FD
    private const val ID_BODY = 0x60D
    private const val ID_INSTRUMENTS = 0x645
    private const val ID_RESTRAINTS = 0x651
    private const val ID_ODOMETER_SLOW = 0x715

    // Confirmed drivetrain and chassis values.
    val steeringAngleDegrees = SignalKey("steering.angle_deg", "Kąt kierownicy")
    val steeringAngularVelocityRaw =
        SignalKey("steering.angular_velocity_raw", "Prędkość obrotu kierownicy (raw)")
    val engineRpmPrimary = SignalKey("engine.rpm.181", "Obroty silnika 0x181")
    val engineRpmSecondary = SignalKey("engine.rpm.1f9", "Obroty silnika 0x1F9")
    val vehicleSpeedKph = SignalKey("vehicle.speed_kph", "Prędkość")
    val clutchPressed181 = SignalKey("pedal.clutch.181", "Sprzęgło")
    val brakePressed181 = SignalKey("pedal.brake.181", "Hamulec 0x181")
    val brakePressed354 = SignalKey("pedal.brake.354", "Hamulec 0x354")
    val parkingBrake = SignalKey("brake.parking", "Hamulec ręczny")
    val coolantTemperature551 =
        SignalKey("temperature.coolant.551", "Temperatura płynu 0x551")
    val coolantTemperature60D =
        SignalKey("temperature.coolant.60d", "Temperatura płynu 0x60D")

    // The same odometer is broadcast in three differently packed fields.
    val odometer5FD = SignalKey("odometer.5fd", "Przebieg 0x5FD")
    val odometer5C5 = SignalKey("odometer.5c5", "Przebieg 0x5C5")
    val odometer715 = SignalKey("odometer.715", "Przebieg 0x715")

    // 0x60D candidate body map, strongly corroborated by the matching captures.
    val doorFrontLeft = SignalKey("door.front_left", "Drzwi lewe przednie")
    val doorFrontRight = SignalKey("door.front_right", "Drzwi prawe przednie")
    val doorRearLeft = SignalKey("door.rear_left", "Drzwi lewe tylne")
    val doorRearRight = SignalKey("door.rear_right", "Drzwi prawe tylne")
    val trunkOpen = SignalKey("door.trunk", "Bagażnik")
    val positionLights = SignalKey("lights.position", "Światła pozycyjne")
    val lowBeamLights = SignalKey("lights.low_beam", "Światła mijania")
    val highBeamLights = SignalKey("lights.high_beam", "Światła drogowe")
    val frontFogLights = SignalKey("lights.front_fog", "Przednie przeciwmgielne")
    val rearFogLights = SignalKey("lights.rear_fog", "Tylne przeciwmgielne")
    val leftTurnSignal = SignalKey("turn.left", "Lewy kierunkowskaz")
    val rightTurnSignal = SignalKey("turn.right", "Prawy kierunkowskaz")
    val ignitionOn = SignalKey("ignition.on", "Zapłon")
    val accessoryPowerOn = SignalKey("ignition.accessory", "Akcesoria")
    val doorsLocked = SignalKey("locks.doors", "Zamek drzwi")
    val trunkLocked = SignalKey("locks.trunk", "Zamek bagażnika")
    val outsideTemperature = SignalKey("temperature.outside", "Temperatura zewnętrzna")
    val reverseGear60D = SignalKey("gear.reverse.60d", "Wsteczny 0x60D")
    val tripComputerUp = SignalKey("stalk.trip_up", "Przycisk manetki góra")
    val tripComputerDown = SignalKey("stalk.trip_down", "Przycisk manetki dół")

    // Additional candidates from the reference capture.
    val reverseGear215 = SignalKey("gear.reverse.215", "Wsteczny 0x215")
    val driverRequestedTorque =
        SignalKey("engine.requested_torque", "Żądany moment silnika")
    val acceleratorPedalRaw = SignalKey("pedal.accelerator_raw", "Pedał gazu (raw)")
    val distanceSinceStart =
        SignalKey("trip.distance_since_start", "Dystans od uruchomienia")
    val fuelUsedSinceStart =
        SignalKey("trip.fuel_used_since_start", "Paliwo od uruchomienia")
    val vehicleAgeMinutes = SignalKey("vehicle.age_minutes", "Wiek pojazdu (licznik)")
    val driverSeatBeltWarning = SignalKey("restraint.driver_belt", "Pas kierowcy")
    val passengerAirbagDisabled =
        SignalKey("restraint.passenger_airbag_disabled", "Airbag pasażera wyłączony")
    val speedSecondary645 = SignalKey("vehicle.speed_kph.645", "Prędkość 0x645")
    val instrumentBacklightRaw =
        SignalKey("instruments.backlight_raw", "Jasność zegarów (raw)")

    /**
     * This wiper layout was already identified in the app while the old parser
     * displayed 0x503. Correcting the Waveshare ID byte order maps that apparent
     * ID to the 0x35D family; the observed frame is retained as 0x35D for testing.
     */
    val wipersMode = SignalKey("wipers.mode", "Tryb wycieraczek")

    val specs: List<SignalSpec> = listOf(
        intSignal(
            key = steeringAngleDegrees,
            canId = ID_STEERING,
            startByte = 0,
            length = 2,
            littleEndian = false,
            scale = 0.1,
            offset = -3_276.8,
            unit = "deg",
        ),
        intSignal(
            key = steeringAngularVelocityRaw,
            canId = ID_STEERING,
            startByte = 2,
            length = 2,
            littleEndian = false,
            offset = -32_768.0,
            unit = "raw",
        ),
        intSignal(
            key = driverRequestedTorque,
            canId = ID_DRIVER_TORQUE,
            startByte = 0,
            length = 1,
            littleEndian = false,
            scale = 2.0,
            offset = -100.0,
            unit = "Nm",
        ),
        intSignal(
            key = engineRpmPrimary,
            canId = ID_ENGINE,
            startByte = 0,
            length = 2,
            littleEndian = false,
            scale = 1.0 / 8.0,
            unit = "rpm",
        ),
        intSignal(
            key = acceleratorPedalRaw,
            canId = ID_ENGINE,
            // In the x0r diagram the accelerator marker is below 0x52: byte 3.
            startByte = 3,
            length = 1,
            littleEndian = false,
            unit = "raw",
        ),
        bitSignal(clutchPressed181, ID_ENGINE, byteIndex = 5, mask = 0x08),
        bitSignal(brakePressed181, ID_ENGINE, byteIndex = 5, mask = 0x01),
        intSignal(
            key = engineRpmSecondary,
            canId = ID_RPM_SECONDARY,
            startByte = 2,
            length = 2,
            littleEndian = false,
            scale = 1.0 / 8.0,
            unit = "rpm",
        ),
        bitSignal(reverseGear215, ID_REVERSE, byteIndex = 1, mask = 0x40),
        intSignal(
            key = vehicleSpeedKph,
            canId = ID_SPEED,
            startByte = 0,
            length = 2,
            littleEndian = false,
            scale = 0.01,
            unit = "km/h",
        ),
        intSignal(
            key = distanceSinceStart,
            canId = ID_SPEED,
            startByte = 2,
            length = 2,
            littleEndian = false,
            scale = 0.1,
            unit = "m",
        ),
        bitSignal(brakePressed354, ID_SPEED, byteIndex = 4, mask = 0x10),
        enumSignal(
            key = wipersMode,
            canId = ID_WIPERS,
            byteIndex = 2,
            mask = 0xC0,
            shift = 6,
            mapping = mapOf(
                0 to "Off",
                1 to "Intermittent",
                2 to "Low",
                3 to "High",
            ),
        ),
        intSignal(
            key = coolantTemperature551,
            canId = ID_COOLANT,
            startByte = 0,
            length = 1,
            littleEndian = false,
            offset = -40.0,
            unit = "degC",
        ),
        intSignal(
            key = fuelUsedSinceStart,
            canId = ID_COOLANT,
            startByte = 1,
            length = 1,
            littleEndian = false,
            scale = 1.0 / 12_500.0,
            unit = "L",
        ),
        bitSignal(
            parkingBrake,
            ID_ODOMETER_PARKING_BRAKE,
            byteIndex = 0,
            mask = 0x08,
        ),
        bigEndianBitFieldSignal(
            key = odometer5C5,
            canId = ID_ODOMETER_PARKING_BRAKE,
            startBit = 12,
            length = 20,
            unit = "km",
        ),
        bigEndianBitFieldSignal(
            key = odometer5FD,
            canId = ID_ODOMETER_AGE,
            startBit = 0,
            length = 20,
            unit = "km",
        ),
        bigEndianBitFieldSignal(
            key = vehicleAgeMinutes,
            canId = ID_ODOMETER_AGE,
            startBit = 20,
            length = 24,
            unit = "min",
        ),
        bitSignal(trunkOpen, ID_BODY, byteIndex = 0, mask = 0x80),
        bitSignal(doorRearRight, ID_BODY, byteIndex = 0, mask = 0x40),
        bitSignal(doorRearLeft, ID_BODY, byteIndex = 0, mask = 0x20),
        bitSignal(doorFrontRight, ID_BODY, byteIndex = 0, mask = 0x10),
        bitSignal(doorFrontLeft, ID_BODY, byteIndex = 0, mask = 0x08),
        bitSignal(positionLights, ID_BODY, byteIndex = 0, mask = 0x04),
        bitSignal(lowBeamLights, ID_BODY, byteIndex = 0, mask = 0x02),
        bitSignal(rightTurnSignal, ID_BODY, byteIndex = 1, mask = 0x40),
        bitSignal(leftTurnSignal, ID_BODY, byteIndex = 1, mask = 0x20),
        bitSignal(highBeamLights, ID_BODY, byteIndex = 1, mask = 0x08),
        bitSignal(ignitionOn, ID_BODY, byteIndex = 1, mask = 0x04),
        bitSignal(accessoryPowerOn, ID_BODY, byteIndex = 1, mask = 0x02),
        bitSignal(frontFogLights, ID_BODY, byteIndex = 1, mask = 0x01),
        bitSignal(doorsLocked, ID_BODY, byteIndex = 2, mask = 0x20),
        bitSignal(trunkLocked, ID_BODY, byteIndex = 2, mask = 0x10),
        bitSignal(rearFogLights, ID_BODY, byteIndex = 2, mask = 0x04),
        intSignal(
            key = outsideTemperature,
            canId = ID_BODY,
            startByte = 4,
            length = 1,
            littleEndian = false,
            offset = -40.0,
            unit = "degC",
        ),
        intSignal(
            key = coolantTemperature60D,
            canId = ID_BODY,
            startByte = 5,
            length = 1,
            littleEndian = false,
            offset = -40.0,
            unit = "degC",
        ),
        bitSignal(reverseGear60D, ID_BODY, byteIndex = 6, mask = 0x10),
        bitSignal(tripComputerUp, ID_BODY, byteIndex = 7, mask = 0x01),
        bitSignal(tripComputerDown, ID_BODY, byteIndex = 7, mask = 0x02),
        intSignal(
            key = instrumentBacklightRaw,
            canId = ID_INSTRUMENTS,
            startByte = 1,
            length = 1,
            littleEndian = false,
            unit = "raw",
        ),
        intSignal(
            key = speedSecondary645,
            canId = ID_INSTRUMENTS,
            startByte = 3,
            length = 2,
            littleEndian = false,
            scale = 0.01,
            unit = "km/h",
        ),
        bitSignal(driverSeatBeltWarning, ID_RESTRAINTS, byteIndex = 1, mask = 0x01),
        bitSignal(passengerAirbagDisabled, ID_RESTRAINTS, byteIndex = 0, mask = 0x02),
        bigEndianBitFieldSignal(
            key = odometer715,
            canId = ID_ODOMETER_SLOW,
            startBit = 4,
            length = 20,
            unit = "km",
        ),
    )

    val groups: List<SignalGroup> = listOf(
        SignalGroup(
            title = "Drivetrain",
            keys = listOf(
                engineRpmPrimary,
                engineRpmSecondary,
                vehicleSpeedKph,
                clutchPressed181,
                brakePressed181,
                brakePressed354,
                acceleratorPedalRaw,
                driverRequestedTorque,
            ),
        ),
        SignalGroup(
            title = "Steering & body",
            keys = listOf(
                steeringAngleDegrees,
                steeringAngularVelocityRaw,
                wipersMode,
                doorFrontLeft,
                doorFrontRight,
                doorRearLeft,
                doorRearRight,
                trunkOpen,
            ),
        ),
        SignalGroup(
            title = "Lights",
            keys = listOf(
                positionLights,
                lowBeamLights,
                highBeamLights,
                frontFogLights,
                rearFogLights,
                leftTurnSignal,
                rightTurnSignal,
            ),
        ),
        SignalGroup(
            title = "Temperatures & counters",
            keys = listOf(
                coolantTemperature60D,
                coolantTemperature551,
                outsideTemperature,
                odometer5FD,
                odometer5C5,
                odometer715,
                distanceSinceStart,
                fuelUsedSinceStart,
            ),
        ),
    )
}

data class SignalGroup(
    val title: String,
    val keys: List<SignalKey>,
)

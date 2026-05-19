package pl.polsatgranie.smartmegane.domain.signal

// Edit only this file to map CAN IDs/bytes/bits to UI signals.
object SignalDefinitions {
    // Byte indices are 0-based (0..7). Update masks/bytes here when you refine the reverse engineering.
    private const val ID_WIPERS_BRAKE = 0x503
    private const val ID_DOORS = 0x506
    private const val ID_STEERING = 0x200
    private const val ID_GYRO = 0x002

    private const val WIPERS_BYTE_INDEX = 2
    private const val PEDALS_BYTE_INDEX = 5
    private const val DOORS_BYTE_INDEX = 2

    val wipersMode = SignalKey("wipers.mode", "Wycieraczki tryb")
    val brakePressed = SignalKey("pedal.brake", "Hamulec")
    val clutchPressed = SignalKey("pedal.clutch", "Sprzeglo")

    val doorFrontLeft = SignalKey("door.front_left", "Drzwi lewy przod")
    val doorFrontRight = SignalKey("door.front_right", "Drzwi prawy przod")
    val doorRearLeft = SignalKey("door.rear_left", "Drzwi lewy tyl")
    val doorRearRight = SignalKey("door.rear_right", "Drzwi prawy tyl")

    val steeringAngleRaw = SignalKey("steering.raw", "Skret kierownicy (raw)")
    val gyroRaw = SignalKey("gyro.raw", "Zyroskop (raw)")

    val specs: List<SignalSpec> = listOf(
        enumSignal(
            key = wipersMode,
            canId = ID_WIPERS_BRAKE,
            byteIndex = WIPERS_BYTE_INDEX,
            mask = 0xC0,
            shift = 6,
            mapping = mapOf(
                0 to "Off",
                1 to "Przerywane",
                2 to "Wolne",
                3 to "Szybkie",
            ),
        ),
        bitSignal(
            key = brakePressed,
            canId = ID_WIPERS_BRAKE,
            byteIndex = PEDALS_BYTE_INDEX,
            mask = 0x10,
        ),
        bitSignal(
            key = clutchPressed,
            canId = ID_WIPERS_BRAKE,
            byteIndex = PEDALS_BYTE_INDEX,
            mask = 0x04,
        ),
        bitSignal(
            key = doorFrontLeft,
            canId = ID_DOORS,
            byteIndex = DOORS_BYTE_INDEX,
            mask = 0x01,
        ),
        bitSignal(
            key = doorFrontRight,
            canId = ID_DOORS,
            byteIndex = DOORS_BYTE_INDEX,
            mask = 0x02,
        ),
        bitSignal(
            key = doorRearLeft,
            canId = ID_DOORS,
            byteIndex = DOORS_BYTE_INDEX,
            mask = 0x04,
        ),
        bitSignal(
            key = doorRearRight,
            canId = ID_DOORS,
            byteIndex = DOORS_BYTE_INDEX,
            mask = 0x08,
        ),
        intSignal(
            key = steeringAngleRaw,
            canId = ID_STEERING,
            startByte = 0,
            length = 4,
            signed = true,
            unit = "raw",
        ),
        intSignal(
            key = gyroRaw,
            canId = ID_GYRO,
            startByte = 0,
            length = 2,
            signed = true,
            unit = "raw",
        ),
    )

    val groups: List<SignalGroup> = listOf(
        SignalGroup(
            title = "Controls",
            keys = listOf(wipersMode, brakePressed, clutchPressed),
        ),
        SignalGroup(
            title = "Doors",
            keys = listOf(doorFrontLeft, doorFrontRight, doorRearLeft, doorRearRight),
        ),
        SignalGroup(
            title = "Steering & gyro",
            keys = listOf(steeringAngleRaw, gyroRaw),
        ),
    )
}

data class SignalGroup(
    val title: String,
    val keys: List<SignalKey>,
)

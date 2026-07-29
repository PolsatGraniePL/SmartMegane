package pl.polsatgranie.smartmegane.domain.signal

import pl.polsatgranie.smartmegane.data.can.CanFrame

/**
 * Canonical CAN-to-signal map for the captured Renault Megane II.
 *
 * Every semantic vehicle value has exactly one CAN source. Replicas identified
 * in the capture (RPM 0x1F9, speed 0x645, coolant 0x60D, odometers 0x5FD/0x715,
 * pedal copies in 0x35D, reverse in 0x215 and lighting copies in 0x625) are not
 * registered, so the application cannot silently switch between transmitters.
 *
 * Byte indices are zero-based. Multi-byte fields are big-endian.
 */
object SignalDefinitions {
    private const val ID_STEERING = 0x0C2
    private const val ID_ENGINE_REQUEST = 0x161
    private const val ID_ENGINE = 0x181
    private const val ID_ENGINE_STATUS = 0x1F9
    private const val ID_GEARBOX_STATUS = 0x215
    private const val ID_ESP_CONTROL = 0x244
    private const val ID_WHEEL_PAIR_A = 0x284
    private const val ID_WHEEL_PAIR_B = 0x285
    private const val ID_INERTIAL = 0x2A0
    private const val ID_SPEED = 0x354
    private const val ID_CLUSTER_CONTROLS = 0x35D
    private const val ID_IMMOBILIZER_RESPONSE = 0x500
    private const val ID_STARTUP_HANDSHAKE = 0x505
    private const val ID_IMMOBILIZER_CHALLENGE = 0x511
    private const val ID_ENGINE_THERMAL = 0x551
    private const val ID_STARTUP_DIAGNOSTIC = 0x581
    private const val ID_ODOMETER_BODY = 0x5C5
    private const val ID_STARTUP_PULSE = 0x5E4
    private const val ID_VEHICLE_TIME = 0x5FD
    private const val ID_BODY = 0x60D
    private const val ID_BODY_ECHO = 0x625
    private const val ID_INSTRUMENTS = 0x645
    private const val ID_RESTRAINTS = 0x651
    private const val ID_ODOMETER_STATUS = 0x715

    // Steering.
    val steeringAngleDegrees = key("steering.angle_deg", "Kąt kierownicy")
    val steeringAngularVelocityRaw =
        key("steering.angular_velocity_raw", "Prędkość obrotu kierownicy (raw)")
    val steeringAliveCounter = key("steering.alive_counter", "Licznik ramki kierownicy")
    val steeringDataValid = key("steering.data_valid", "Ważność danych kierownicy")
    val steeringStartupFlag = key("steering.startup", "Rozruch nadajnika kierownicy")
    val steeringChecksum = key("steering.checksum", "Suma kontrolna kierownicy")

    // Engine, pedals and driveline.
    val driverRequestedTorque =
        key("engine.requested_torque_nm", "Żądany moment silnika")
    val engineTorqueLikeRaw =
        key("engine.torque_like_161_raw", "Pole momentu 0x161 (raw)")
    val engineAnalog161Raw =
        key("engine.analog_161_raw", "Pole analogowe 0x161 (raw)")
    val engineLoadRaw = key("engine.load_like_raw", "Obciążenie silnika (raw)")
    val engineRpm = key("engine.rpm", "Obroty silnika")
    val acceleratorPedalRaw = key("pedal.accelerator_raw", "Pedał gazu (raw)")
    val engineState181Raw = key("engine.state_181_raw", "Stan silnika 0x181 (raw)")
    val engineTorque181Raw = key("engine.torque_181_raw", "Moment 0x181 (raw)")
    val brakePressed = key("pedal.brake", "Pedał hamulca")
    val clutchPressed = key("pedal.clutch", "Pedał sprzęgła")
    val pedalFrameStatusRaw = key("pedal.frame_status_raw", "Status ramki pedałów")
    val engineAnalog181Raw =
        key("engine.analog_181_raw", "Pole analogowe 0x181 (raw)")
    val engineDataValid = key("engine.data_valid", "Ważność danych silnika")
    val engineState1F9Raw =
        key("engine.state_1f9_raw", "Stan nadajnika obrotów 0x1F9")
    val engineStatus1F9Raw =
        key("engine.status_1f9_raw", "Status silnika 0x1F9 (raw)")
    val gearboxEngineAnalog0Raw =
        key("gearbox.engine_analog_0_raw", "Pole silnika 0x215 b0")
    val gearboxFrameStatusRaw =
        key("gearbox.frame_status_raw", "Status ramki skrzyni 0x215")
    val gearboxEngineAnalog2Raw =
        key("gearbox.engine_analog_2_raw", "Pole silnika 0x215 b2")
    val gearboxDuplicatedEngineRaw =
        key("gearbox.duplicated_engine_raw", "Zdublowane pole silnika 0x215")
    val reverseGear = key("gear.reverse", "Bieg wsteczny")

    // ESP/ABS and kinematics.
    val asrEspButtonPressed =
        key("esp.off_button", "Przycisk wyłączenia ASR/ESP")
    val asrEspButtonEcho =
        key("esp.off_button_echo", "Potwierdzenie przycisku ASR/ESP")
    val asrEspButtonReleasePulse =
        key("esp.off_button_release", "Impuls zwolnienia przycisku ASR/ESP")
    val asrEspDisabled = key("esp.disabled", "ASR/ESP wyłączone")
    val wheelPairAFirstRaw = key("wheel.pair_a.first_raw", "Koło A1 (raw)")
    val wheelPairASecondRaw = key("wheel.pair_a.second_raw", "Koło A2 (raw)")
    val wheelPairAAliveCounter = key("wheel.pair_a.alive_counter", "Licznik ABS A")
    val wheelPairAChecksum = key("wheel.pair_a.checksum", "Suma kontrolna ABS A")
    val wheelPairBFirstRaw = key("wheel.pair_b.first_raw", "Koło B1 (raw)")
    val wheelPairBSecondRaw = key("wheel.pair_b.second_raw", "Koło B2 (raw)")
    val wheelPairBAliveCounter = key("wheel.pair_b.alive_counter", "Licznik ABS B")
    val wheelPairBChecksum = key("wheel.pair_b.checksum", "Suma kontrolna ABS B")
    val yawRaw = key("inertial.yaw_raw", "Odchylenie/yaw (raw)")
    val inertialAxisARaw = key("inertial.axis_a_raw", "Oś inercyjna A (raw)")
    val inertialAxisBRaw = key("inertial.axis_b_raw", "Oś inercyjna B (raw)")
    val vehicleSpeedKph = key("vehicle.speed_kph", "Prędkość")
    val distanceSinceStart =
        key("trip.distance_since_start_m", "Dystans od uruchomienia")
    val speedDataInvalid = key("vehicle.speed_invalid", "Brak ważnej prędkości")

    // Cluster controls.
    val clusterNetworkActive =
        key("cluster.network_active", "Aktywna sieć zestawu wskaźników")
    val clusterWakeup = key("cluster.wakeup", "Wybudzenie zestawu wskaźników")
    val rearDefrostOn = key("climate.rear_defrost", "Ogrzewanie tylnej szyby")
    val wipersMode = key("wipers.mode", "Tryb wycieraczek")
    val engineRunning = key("engine.running", "Silnik pracuje")
    val clusterTransition =
        key("cluster.transition", "Przejście zestawu wskaźników")

    // Security/startup diagnostic fields are kept opaque.
    val immobilizerResponseControl =
        key("immobilizer.response_control", "Kontrola odpowiedzi immobilizera")
    val immobilizerResponseToken =
        key("immobilizer.response_token", "Token odpowiedzi immobilizera")
    val startupHandshake =
        key("startup.handshake_505", "Ramka startowa 0x505")
    val immobilizerChallengeControl =
        key("immobilizer.challenge_control", "Kontrola wyzwania immobilizera")
    val immobilizerChallengeToken =
        key("immobilizer.challenge_token", "Token wyzwania immobilizera")
    val startupDiagnostic =
        key("startup.diagnostic_581", "Ramka startowa 0x581")
    val startupPulse = key("startup.pulse_5e4", "Impuls startowy 0x5E4")

    // Temperatures, fuel and counters.
    val coolantTemperature = key("temperature.coolant", "Temperatura płynu")
    val fuelUsedSinceStart =
        key("trip.fuel_used_since_start_l", "Paliwo od uruchomienia")
    val engineThermalFrameStatusRaw =
        key("engine.thermal_frame_status_raw", "Status ramki temperatury/paliwa")
    val fuelLevelRaw = key("fuel.level_raw", "Poziom paliwa (raw)")
    val exteriorLightsPresent =
        key("lights.exterior_aggregate", "Zbiorczy stan świateł zewnętrznych")
    val parkingBrake = key("brake.parking", "Hamulec ręczny")
    val odometer = key("odometer.km", "Przebieg")
    val odometerFrameTransition =
        key("odometer.frame_transition", "Przejście ramki przebiegu")
    val serviceStatusRaw = key("service.status_raw", "Status serwisu (raw)")
    val vehicleAgeMinutes = key("vehicle.age_minutes", "Wiek pojazdu (licznik)")
    val vehicleTimeFrameTransition =
        key("vehicle.time_frame_transition", "Przejście ramki czasu")
    val odometerStatusRaw =
        key("odometer.status_raw", "Status repliki przebiegu 0x715")

    // Body, lights and controls. All user-facing fields use the canonical 0x60D.
    val trunkOpen = key("door.trunk", "Bagażnik")
    val doorFrontLeft = key("door.front_left", "Drzwi lewe przednie")
    val doorFrontRight = key("door.front_right", "Drzwi prawe przednie")
    val doorRearLeft = key("door.rear_left", "Drzwi lewe tylne")
    val doorRearRight = key("door.rear_right", "Drzwi prawe tylne")
    val lowBeamLights = key("lights.low_beam", "Światła mijania")
    val positionLights = key("lights.position", "Światła pozycyjne")
    val frontFogLights = key("lights.front_fog", "Przednie przeciwmgielne")
    val highBeamLights = key("lights.high_beam", "Światła drogowe")
    val accessoryPowerOn = key("ignition.accessory", "Akcesoria")
    val ignitionOn = key("ignition.on", "Zapłon RUN")
    val leftTurnSignal = key("turn.left", "Lewy kierunkowskaz")
    val rightTurnSignal = key("turn.right", "Prawy kierunkowskaz")
    val trunkLocked = key("locks.trunk", "Zamek bagażnika")
    val doorsLocked = key("locks.doors", "Zamek drzwi")
    val rearFogLights = key("lights.rear_fog", "Tylne przeciwmgielne")
    val outsideTemperature = key("temperature.outside", "Temperatura zewnętrzna")
    val reverseFrameStatusRaw =
        key("gear.reverse_frame_status_raw", "Status ramki biegu wstecznego")
    val tripComputerDown = key("stalk.trip_down", "Przycisk manetki dół")
    val tripComputerUp = key("stalk.trip_up", "Przycisk manetki góra")
    val bodySensorRaw = key("body.sensor_raw", "Czujnik nadwozia 0x625 (raw)")
    val bodyStatusRaw = key("body.status_raw", "Status nadwozia 0x625")
    val clusterDataValid = key("cluster.data_valid", "Ważność danych zegarów")
    val instrumentBacklightRaw =
        key("instruments.backlight_raw", "Jasność zegarów (raw)")
    val passengerAirbagDisabled =
        key("restraint.passenger_airbag_disabled", "Airbag pasażera wyłączony")
    val restraintStartup =
        key("restraint.startup", "Rozruch systemu poduszek/pasów")
    val driverSeatBeltWarning =
        key("restraint.driver_belt", "Pas kierowcy niezapięty")

    val specs: List<SignalSpec> = listOf(
        intSignal(
            steeringAngleDegrees,
            ID_STEERING,
            startByte = 0,
            length = 2,
            littleEndian = false,
            scale = 0.1,
            offset = -3_276.8,
            unit = "deg",
            validWhen = ::steeringFrameValid,
        ),
        intSignal(
            steeringAngularVelocityRaw,
            ID_STEERING,
            startByte = 2,
            length = 2,
            littleEndian = false,
            offset = -32_768.0,
            unit = "raw",
            validWhen = ::steeringFrameValid,
        ),
        maskedIntSignal(steeringAliveCounter, ID_STEERING, 4, 0x0F),
        bitSignal(steeringDataValid, ID_STEERING, 4, 0x10),
        bitSignal(steeringStartupFlag, ID_STEERING, 4, 0x80),
        intSignal(steeringChecksum, ID_STEERING, 5, 1, littleEndian = false),

        intSignal(
            driverRequestedTorque,
            ID_ENGINE_REQUEST,
            0,
            1,
            littleEndian = false,
            scale = 2.0,
            offset = -100.0,
            unit = "Nm",
        ),
        intSignal(engineTorqueLikeRaw, ID_ENGINE_REQUEST, 1, 1, littleEndian = false),
        intSignal(engineAnalog161Raw, ID_ENGINE_REQUEST, 2, 1, littleEndian = false),
        intSignal(engineLoadRaw, ID_ENGINE_REQUEST, 4, 1, littleEndian = false),

        intSignal(
            engineRpm,
            ID_ENGINE,
            0,
            2,
            littleEndian = false,
            scale = 1.0 / 8.0,
            unit = "rpm",
            validWhen = ::engineFrameValid,
        ),
        intSignal(acceleratorPedalRaw, ID_ENGINE, 2, 1, littleEndian = false),
        intSignal(engineState181Raw, ID_ENGINE, 3, 1, littleEndian = false),
        intSignal(engineTorque181Raw, ID_ENGINE, 4, 1, littleEndian = false),
        bitSignal(brakePressed, ID_ENGINE, 5, 0x01),
        bitSignal(clutchPressed, ID_ENGINE, 5, 0x08),
        maskedIntSignal(pedalFrameStatusRaw, ID_ENGINE, 5, 0x64),
        intSignal(engineAnalog181Raw, ID_ENGINE, 6, 1, littleEndian = false),
        bitSignal(engineDataValid, ID_ENGINE, 7, 0x02),

        intSignal(engineState1F9Raw, ID_ENGINE_STATUS, 0, 1, littleEndian = false),
        intSignal(engineStatus1F9Raw, ID_ENGINE_STATUS, 4, 2, littleEndian = false),
        intSignal(gearboxEngineAnalog0Raw, ID_GEARBOX_STATUS, 0, 1, littleEndian = false),
        maskedIntSignal(gearboxFrameStatusRaw, ID_GEARBOX_STATUS, 1, 0xBF),
        intSignal(gearboxEngineAnalog2Raw, ID_GEARBOX_STATUS, 2, 1, littleEndian = false),
        intSignal(
            gearboxDuplicatedEngineRaw,
            ID_GEARBOX_STATUS,
            4,
            1,
            littleEndian = false,
        ),

        bitSignal(asrEspButtonPressed, ID_ESP_CONTROL, 3, 0x01),
        bitSignal(asrEspButtonEcho, ID_ESP_CONTROL, 5, 0x10),
        bitSignal(asrEspButtonReleasePulse, ID_ESP_CONTROL, 5, 0x08),

        intSignal(
            wheelPairAFirstRaw,
            ID_WHEEL_PAIR_A,
            0,
            2,
            littleEndian = false,
            validWhen = { frame -> be16(frame, 0) != 0xFFFF },
        ),
        intSignal(
            wheelPairASecondRaw,
            ID_WHEEL_PAIR_A,
            2,
            2,
            littleEndian = false,
            validWhen = { frame -> be16(frame, 2) != 0xFFFF },
        ),
        intSignal(wheelPairAAliveCounter, ID_WHEEL_PAIR_A, 6, 1, littleEndian = false),
        intSignal(wheelPairAChecksum, ID_WHEEL_PAIR_A, 7, 1, littleEndian = false),
        intSignal(
            wheelPairBFirstRaw,
            ID_WHEEL_PAIR_B,
            0,
            2,
            littleEndian = false,
            validWhen = { frame -> be16(frame, 0) != 0xFFFF },
        ),
        intSignal(
            wheelPairBSecondRaw,
            ID_WHEEL_PAIR_B,
            2,
            2,
            littleEndian = false,
            validWhen = { frame -> be16(frame, 2) != 0xFFFF },
        ),
        intSignal(wheelPairBAliveCounter, ID_WHEEL_PAIR_B, 6, 1, littleEndian = false),
        intSignal(wheelPairBChecksum, ID_WHEEL_PAIR_B, 7, 1, littleEndian = false),

        intSignal(yawRaw, ID_INERTIAL, 0, 1, littleEndian = false),
        intSignal(
            inertialAxisARaw,
            ID_INERTIAL,
            1,
            2,
            littleEndian = false,
            offset = -32_767.0,
            validWhen = { frame -> be16(frame, 1) != 0x7FFF },
        ),
        intSignal(
            inertialAxisBRaw,
            ID_INERTIAL,
            3,
            2,
            littleEndian = false,
            offset = -32_767.0,
            validWhen = { frame -> be16(frame, 3) != 0x7FFF },
        ),

        intSignal(
            vehicleSpeedKph,
            ID_SPEED,
            0,
            2,
            littleEndian = false,
            scale = 0.01,
            unit = "km/h",
            validWhen = ::speedFrameValid,
        ),
        intSignal(
            distanceSinceStart,
            ID_SPEED,
            2,
            2,
            littleEndian = false,
            scale = 0.1,
            unit = "m",
        ),
        bitSignal(asrEspDisabled, ID_SPEED, 4, 0x40),
        bitSignal(speedDataInvalid, ID_SPEED, 6, 0x10),

        bitSignal(clusterNetworkActive, ID_CLUSTER_CONTROLS, 0, 0x80),
        bitSignal(clusterWakeup, ID_CLUSTER_CONTROLS, 0, 0x40),
        maskedBoolSignal(rearDefrostOn, ID_CLUSTER_CONTROLS, 0, 0x06, 0x06),
        enumSignal(
            wipersMode,
            ID_CLUSTER_CONTROLS,
            byteIndex = 2,
            mask = 0xE0,
            shift = 5,
            mapping = mapOf(
                0 to "Off",
                1 to "Intermittent",
                6 to "SingleOrLow",
                7 to "High",
            ),
        ),
        bitSignal(engineRunning, ID_CLUSTER_CONTROLS, 5, 0x01),
        bitSignal(clusterTransition, ID_CLUSTER_CONTROLS, 6, 0x04),

        intSignal(
            immobilizerResponseControl,
            ID_IMMOBILIZER_RESPONSE,
            0,
            1,
            littleEndian = false,
        ),
        intSignal(
            immobilizerResponseToken,
            ID_IMMOBILIZER_RESPONSE,
            1,
            4,
            littleEndian = false,
        ),
        intSignal(startupHandshake, ID_STARTUP_HANDSHAKE, 0, 3, littleEndian = false),
        intSignal(
            immobilizerChallengeControl,
            ID_IMMOBILIZER_CHALLENGE,
            0,
            1,
            littleEndian = false,
        ),
        intSignal(
            immobilizerChallengeToken,
            ID_IMMOBILIZER_CHALLENGE,
            1,
            6,
            littleEndian = false,
        ),

        intSignal(
            coolantTemperature,
            ID_ENGINE_THERMAL,
            0,
            1,
            littleEndian = false,
            offset = -40.0,
            unit = "degC",
        ),
        intSignal(
            fuelUsedSinceStart,
            ID_ENGINE_THERMAL,
            1,
            1,
            littleEndian = false,
            scale = 1.0 / 12_500.0,
            unit = "L",
        ),
        intSignal(
            engineThermalFrameStatusRaw,
            ID_ENGINE_THERMAL,
            3,
            1,
            littleEndian = false,
        ),
        intSignal(fuelLevelRaw, ID_ENGINE_THERMAL, 5, 1, littleEndian = false),
        intSignal(startupDiagnostic, ID_STARTUP_DIAGNOSTIC, 0, 4, littleEndian = false),

        maskedBoolSignal(exteriorLightsPresent, ID_ODOMETER_BODY, 0, 0xC0, 0x40),
        bitSignal(parkingBrake, ID_ODOMETER_BODY, 0, 0x04),
        bigEndianBitFieldSignal(
            odometer,
            ID_ODOMETER_BODY,
            startBit = 12,
            length = 20,
            unit = "km",
        ),
        maskedIntSignal(
            odometerFrameTransition,
            ID_ODOMETER_BODY,
            byteIndex = 4,
            mask = 0x10,
            shift = 4,
        ),
        intSignal(serviceStatusRaw, ID_ODOMETER_BODY, 7, 1, littleEndian = false),
        bitSignal(startupPulse, ID_STARTUP_PULSE, 0, 0x01),
        bigEndianBitFieldSignal(
            vehicleAgeMinutes,
            ID_VEHICLE_TIME,
            startBit = 20,
            length = 24,
            unit = "min",
        ),
        maskedIntSignal(
            vehicleTimeFrameTransition,
            ID_VEHICLE_TIME,
            byteIndex = 7,
            mask = 0xF0,
            shift = 4,
        ),

        bitSignal(trunkOpen, ID_BODY, 0, 0x80),
        bitSignal(doorRearRight, ID_BODY, 0, 0x40),
        bitSignal(doorRearLeft, ID_BODY, 0, 0x20),
        bitSignal(doorFrontRight, ID_BODY, 0, 0x10),
        bitSignal(doorFrontLeft, ID_BODY, 0, 0x08),
        bitSignal(positionLights, ID_BODY, 0, 0x04),
        bitSignal(lowBeamLights, ID_BODY, 0, 0x02),
        bitSignal(rightTurnSignal, ID_BODY, 1, 0x40),
        bitSignal(leftTurnSignal, ID_BODY, 1, 0x20),
        bitSignal(highBeamLights, ID_BODY, 1, 0x08),
        bitSignal(ignitionOn, ID_BODY, 1, 0x04),
        bitSignal(accessoryPowerOn, ID_BODY, 1, 0x02),
        bitSignal(frontFogLights, ID_BODY, 1, 0x01),
        bitSignal(doorsLocked, ID_BODY, 2, 0x20),
        bitSignal(trunkLocked, ID_BODY, 2, 0x10),
        bitSignal(rearFogLights, ID_BODY, 2, 0x04),
        intSignal(
            outsideTemperature,
            ID_BODY,
            4,
            1,
            littleEndian = false,
            offset = -40.0,
            unit = "degC",
        ),
        maskedIntSignal(reverseFrameStatusRaw, ID_BODY, 6, 0x2F),
        bitSignal(
            reverseGear,
            ID_BODY,
            byteIndex = 6,
            mask = 0x10,
            validWhen = ::reverseFrameValid,
        ),
        bitSignal(tripComputerUp, ID_BODY, 7, 0x01),
        bitSignal(tripComputerDown, ID_BODY, 7, 0x02),

        intSignal(bodySensorRaw, ID_BODY_ECHO, 2, 1, littleEndian = false),
        intSignal(bodyStatusRaw, ID_BODY_ECHO, 3, 1, littleEndian = false),
        bitSignal(clusterDataValid, ID_INSTRUMENTS, 0, 0x40),
        intSignal(
            instrumentBacklightRaw,
            ID_INSTRUMENTS,
            1,
            1,
            littleEndian = false,
            validWhen = { frame -> byte(frame, 1) != 0xFF },
        ),
        bitSignal(passengerAirbagDisabled, ID_RESTRAINTS, 0, 0x02),
        bitSignal(restraintStartup, ID_RESTRAINTS, 0, 0x08),
        bitSignal(driverSeatBeltWarning, ID_RESTRAINTS, 1, 0x01),
        intSignal(odometerStatusRaw, ID_ODOMETER_STATUS, 3, 1, littleEndian = false),
    )

    val groups: List<SignalGroup> = listOf(
        SignalGroup(
            title = "Drivetrain",
            keys = listOf(
                engineRpm,
                vehicleSpeedKph,
                acceleratorPedalRaw,
                clutchPressed,
                brakePressed,
                reverseGear,
                driverRequestedTorque,
                engineRunning,
            ),
        ),
        SignalGroup(
            title = "Steering, ABS & body",
            keys = listOf(
                steeringAngleDegrees,
                steeringAngularVelocityRaw,
                wheelPairAFirstRaw,
                wheelPairASecondRaw,
                wheelPairBFirstRaw,
                wheelPairBSecondRaw,
                wipersMode,
                rearDefrostOn,
                doorFrontLeft,
                doorFrontRight,
                doorRearLeft,
                doorRearRight,
                trunkOpen,
            ),
        ),
        SignalGroup(
            title = "Lights & controls",
            keys = listOf(
                positionLights,
                lowBeamLights,
                highBeamLights,
                frontFogLights,
                rearFogLights,
                leftTurnSignal,
                rightTurnSignal,
                asrEspDisabled,
                driverSeatBeltWarning,
                parkingBrake,
            ),
        ),
        SignalGroup(
            title = "Temperatures & counters",
            keys = listOf(
                coolantTemperature,
                outsideTemperature,
                fuelLevelRaw,
                fuelUsedSinceStart,
                odometer,
                distanceSinceStart,
                vehicleAgeMinutes,
            ),
        ),
    )

    private fun key(id: String, label: String) = SignalKey(id, label)

    private fun steeringFrameValid(frame: CanFrame): Boolean =
        frame.dlc > 4 &&
            byte(frame, 4) and 0x10 != 0 &&
            be16(frame, 0) != 0xFFFF

    private fun engineFrameValid(frame: CanFrame): Boolean =
        frame.dlc > 7 && byte(frame, 7) and 0x02 != 0

    private fun speedFrameValid(frame: CanFrame): Boolean =
        frame.dlc > 6 &&
            be16(frame, 0) != 0xFFFF &&
            byte(frame, 6) and 0x10 == 0

    private fun reverseFrameValid(frame: CanFrame): Boolean =
        frame.dlc > 6 && byte(frame, 6) and 0x0F == 0x01

    private fun be16(frame: CanFrame, startByte: Int): Int =
        if (startByte + 1 < frame.dlc) {
            (byte(frame, startByte) shl 8) or byte(frame, startByte + 1)
        } else {
            -1
        }

    private fun byte(frame: CanFrame, index: Int): Int =
        frame.data.getOrNull(index)?.toInt()?.and(0xFF) ?: -1
}

data class SignalGroup(
    val title: String,
    val keys: List<SignalKey>,
)

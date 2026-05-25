package dev.vector.proxy.protocol

enum class ProtocolVersion(val protocol: Int, val versionString: String) {
    UNKNOWN(-1, "Unknown"),
    MINECRAFT_1_7_2(4, "1.7.2"),
    MINECRAFT_1_7_10(5, "1.7.10"),
    MINECRAFT_1_8(47, "1.8"),
    MINECRAFT_1_9(107, "1.9"),
    MINECRAFT_1_9_4(110, "1.9.4"),
    MINECRAFT_1_10(210, "1.10"),
    MINECRAFT_1_11(315, "1.11"),
    MINECRAFT_1_12(335, "1.12"),
    MINECRAFT_1_12_2(340, "1.12.2"),
    MINECRAFT_1_13(393, "1.13"),
    MINECRAFT_1_14(477, "1.14"),
    MINECRAFT_1_15(573, "1.15"),
    MINECRAFT_1_16(735, "1.16"),
    MINECRAFT_1_16_4(754, "1.16.4/1.16.5"),
    MINECRAFT_1_17(755, "1.17"),
    MINECRAFT_1_17_1(756, "1.17.1"),
    MINECRAFT_1_18(757, "1.18/1.18.1"),
    MINECRAFT_1_18_2(758, "1.18.2"),
    MINECRAFT_1_19(759, "1.19"),
    MINECRAFT_1_19_1(760, "1.19.1/1.19.2"),
    MINECRAFT_1_19_3(761, "1.19.3"),
    MINECRAFT_1_19_4(762, "1.19.4"),
    MINECRAFT_1_20(763, "1.20/1.20.1"),
    MINECRAFT_1_20_2(764, "1.20.2"),
    MINECRAFT_1_20_3(765, "1.20.3/1.20.4"),
    MINECRAFT_1_20_5(766, "1.20.5/1.20.6"),
    MINECRAFT_1_21(767, "1.21/1.21.1"),
    MINECRAFT_1_21_2(768, "1.21.2/1.21.3"),
    MINECRAFT_1_21_4(769, "1.21.4");

    companion object {
        private val BY_PROTOCOL = entries.associateBy { it.protocol }

        fun fromProtocol(protocol: Int): ProtocolVersion = BY_PROTOCOL[protocol] ?: UNKNOWN

        val MINIMUM = MINECRAFT_1_7_2
        val MAXIMUM = MINECRAFT_1_21_4
    }
}

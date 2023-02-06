package com.reach_android.bluetooth

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*

enum class CharacteristicFormat(val value: UByte) {
    UNKNOWN(0.toUByte()),
    BOOL(1.toUByte()),

    UINT2(2.toUByte()),
    UINT4(3.toUByte()),
    UINT8(4.toUByte()),
    UINT12(5.toUByte()),
    UINT16(6.toUByte()),
    UINT24(7.toUByte()),
    UINT32(8.toUByte()),
    UINT48(9.toUByte()),
    UINT64(10.toUByte()),
    UINT128(11.toUByte()),

    INT8(12.toUByte()),
    INT12(13.toUByte()),
    INT16(14.toUByte()),
    INT24(15.toUByte()),
    INT32(16.toUByte()),
    INT48(17.toUByte()),
    INT64(18.toUByte()),
    INT128(19.toUByte()),

    FLOAT32(20.toUByte()),
    FLOAT64(21.toUByte()),


    MedFloat16(22.toUByte()), // Unsupported
    MedFloat32(23.toUByte()), // Unsupported
    MedNomCode(24.toUByte()), // Unsupported

    UTF8(25.toUByte()),
    UTF16(26.toUByte()),

    STRUCT(27.toUByte());

    companion object {
        fun fromValue(value: UByte): CharacteristicFormat {
            return reverse[value] ?: UNKNOWN
        }

        private val reverse by lazy {
            values().associateBy { it.value }
        }
    }
}


data class CharacteristicPresentationFormat(
    val format: CharacteristicFormat,
    val exponent: Byte,
    val unit: UShort,
    val namespace: UByte,
    val description: UShort
) {
    val isValid get() = format != CharacteristicFormat.UNKNOWN
    val unitString = unitMap[unit]
    val descriptionString = if (namespace == 0x01.toUByte()) descriptionMap[description] else null

    fun formatValue(value: ByteArray): String? = when (format) {
        CharacteristicFormat.BOOL -> {
            (value[0] == 1.toByte()).toString()
        }
        CharacteristicFormat.UINT2,
        CharacteristicFormat.UINT4,
        CharacteristicFormat.UINT8,
        CharacteristicFormat.UINT12,
        CharacteristicFormat.UINT16,
        CharacteristicFormat.UINT24,
        CharacteristicFormat.UINT32,
        CharacteristicFormat.UINT48,
        CharacteristicFormat.UINT64 -> {
            BigInteger(1, value.reversedArray()).toBigDecimal(exponent.toInt()).toString()
        }
        CharacteristicFormat.INT8,
        CharacteristicFormat.INT12,
        CharacteristicFormat.INT16,
        CharacteristicFormat.INT24,
        CharacteristicFormat.INT32,
        CharacteristicFormat.INT48,
        CharacteristicFormat.INT64 -> {
            BigInteger(value.reversedArray()).toBigDecimal(exponent.toInt()).toString()
        }
        CharacteristicFormat.FLOAT32 -> {
            ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).float.toString()
        }
        CharacteristicFormat.FLOAT64 -> {
            ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).double.toString()
        }
        CharacteristicFormat.UTF8 -> {
            StandardCharsets.UTF_8.decode(ByteBuffer.wrap(value)).toString()
        }
        CharacteristicFormat.UTF16 -> {
            StandardCharsets.UTF_16.decode(ByteBuffer.wrap(value)).toString()
        }
        else -> null
    }?.let {
        unitString?.let { u -> "$it $u" } ?: it
    }?.let {
        descriptionString?.let { d -> "$it ($d)" } ?: it
    }

    companion object {
        val descriptorId: UUID = UUID.fromString("00002904-0000-1000-8000-00805F9B34FB")

        fun parse(format: ByteArray): CharacteristicPresentationFormat {
            val bytes = ByteBuffer.wrap(format).order(ByteOrder.LITTLE_ENDIAN)
            val formatByte = bytes.get().toUByte()
            val exponent = bytes.get()
            val unit = bytes.short.toUShort()
            val namespace = bytes.get().toUByte()
            val description = bytes.short.toUShort()

            return CharacteristicPresentationFormat(
                CharacteristicFormat.fromValue(formatByte),
                exponent, unit, namespace, description
            )
        }

        private val unitMap by lazy {
            hashMapOf(
//                0x2700.toUShort() to "",
                0x2701.toUShort() to "Meters",
                0x2702.toUShort() to "Kilograms",
                0x2703.toUShort() to "Seconds",
                0x2704.toUShort() to "Amperes",
                0x2705.toUShort() to "K",
                0x2706.toUShort() to "Moles",
                0x2707.toUShort() to "Candelas",
                0x2710.toUShort() to "m2",
                0x2711.toUShort() to "m3",
                0x2712.toUShort() to "m/s",
                0x2713.toUShort() to "m/s2",
                0x2714.toUShort() to "Wavenumber",
                0x2715.toUShort() to "kg/m3",
                0x2716.toUShort() to "kg/m2",
                0x2717.toUShort() to "m3/kg",
                0x2718.toUShort() to "A/m2",
                0x2719.toUShort() to "A/m",
                0x271A.toUShort() to "mol/m3",
                0x271B.toUShort() to "kg/m3",
                0x271C.toUShort() to "cd/m2",
                0x271D.toUShort() to "n",
                0x271E.toUShort() to "Kri",
                0x2720.toUShort() to "Radians",
                0x2721.toUShort() to "Steradians",
                0x2722.toUShort() to "Hz",
                0x2723.toUShort() to "N",
                0x2724.toUShort() to "Pa",
                0x2725.toUShort() to "Joules",
                0x2726.toUShort() to "Watts",
                0x2727.toUShort() to "Coulombs",
                0x2728.toUShort() to "Volts",
                0x2729.toUShort() to "Farads",
                0x272A.toUShort() to "Ohms",
                0x272B.toUShort() to "Siemens",
                0x272C.toUShort() to "Webers",
                0x272D.toUShort() to "Teslas",
                0x272E.toUShort() to "H",
                0x272F.toUShort() to "C",
                0x2730.toUShort() to "Lumens",
                0x2731.toUShort() to "Lux",
                0x2732.toUShort() to "Bq",
                0x2733.toUShort() to "Gy",
                0x2734.toUShort() to "Sv",
                0x2735.toUShort() to "kat",
                0x2740.toUShort() to "Pa/s",
                0x2741.toUShort() to "Nm",
                0x2742.toUShort() to "N/m",
                0x2743.toUShort() to "rad/s",
                0x2744.toUShort() to "rad/s2",
                0x2745.toUShort() to "W/m2)",
                0x2746.toUShort() to "J/K0",
                0x2747.toUShort() to "J/kgK",
                0x2748.toUShort() to "J/kg",
                0x2749.toUShort() to "W/(mK)",
                0x274A.toUShort() to "J/m3",
                0x274B.toUShort() to "V/m",
                0x274C.toUShort() to "Coulomb/m3",
                0x274D.toUShort() to "Coulomb/m2",
                0x274E.toUShort() to "Coulomb/m2",
                0x274F.toUShort() to "Farad/m",
                0x2750.toUShort() to "H/m",
                0x2751.toUShort() to "Joule/mole",
                0x2752.toUShort() to "J/molK",
                0x2753.toUShort() to "Coulomb/kg",
                0x2754.toUShort() to "Gy/s",
                0x2755.toUShort() to "W/sr",
                0x2756.toUShort() to "W/m2sr",
                0x2757.toUShort() to "Katal/m3",
                0x2760.toUShort() to "Minutes",
                0x2761.toUShort() to "Hours",
                0x2762.toUShort() to "Days",
                0x2763.toUShort() to "Degrees",
                0x2764.toUShort() to "Minutes",
                0x2765.toUShort() to "Seconds",
                0x2766.toUShort() to "Hectares",
                0x2767.toUShort() to "Litres",
                0x2768.toUShort() to "Tonnes",
                0x2780.toUShort() to "bar",
                0x2781.toUShort() to "mmHg",
                0x2782.toUShort() to "Angstroms",
                0x2783.toUShort() to "NM",
                0x2784.toUShort() to "Barns",
                0x2785.toUShort() to "Knots",
                0x2786.toUShort() to "Nepers",
                0x2787.toUShort() to "bel",
                0x27A0.toUShort() to "Yards",
                0x27A1.toUShort() to "Parsecs",
                0x27A2.toUShort() to "Inches",
                0x27A3.toUShort() to "Feet",
                0x27A4.toUShort() to "Miles",
                0x27A5.toUShort() to "psi",
                0x27A6.toUShort() to "KPH",
                0x27A7.toUShort() to "MPH",
                0x27A8.toUShort() to "RPM",
                0x27A9.toUShort() to "cal",
                0x27AA.toUShort() to "Cal",
                0x27AB.toUShort() to "kWh",
                0x27AC.toUShort() to "F",
                0x27AD.toUShort() to "Percent",
                0x27AE.toUShort() to "Per Mile",
                0x27AF.toUShort() to "bp/m",
                0x27B0.toUShort() to "Ah",
                0x27B1.toUShort() to "mg/Decilitre",
                0x27B2.toUShort() to "mmol/l",
                0x27B3.toUShort() to "Years",
                0x27B4.toUShort() to "Months",
                0x27B5.toUShort() to "Count/m3",
                0x27B6.toUShort() to "Watt/m2",
                0x27B7.toUShort() to "ml/kg/min",
                0x27B8.toUShort() to "lbs",
                0x27B9.toUShort() to "metabolic equivalent",
                0x27BA.toUShort() to "step / minute",
                0x27BC.toUShort() to "stroke / minute",
                0x27BD.toUShort() to "km/mile",
                0x27BE.toUShort() to "lumen per watt",
                0x27BF.toUShort() to "lumen hour",
                0x27C0.toUShort() to "lux hour",
                0x27C1.toUShort() to "g/s",
                0x27C2.toUShort() to "l/s",
                0x27C3.toUShort() to "db",
                0x27C4.toUShort() to "ppm",
                0x27C5.toUShort() to "ppb",
                0x27C6.toUShort() to "mg/dl/min",
                0x27C7.toUShort() to "kilovolt ampere hour",
                0x27C8.toUShort() to "volt ampere",

                )
        }

        private val descriptionMap by lazy {
            hashMapOf(
//                0x0000.toUShort() to "unknown",
                0x0001.toUShort() to "first",
                0x0002.toUShort() to "second",
                0x0003.toUShort() to "third",
                0x0004.toUShort() to "fourth",
                0x0005.toUShort() to "fifth",
                0x0006.toUShort() to "sixth",
                0x0007.toUShort() to "seventh",
                0x0008.toUShort() to "eighth",
                0x0009.toUShort() to "ninth",
                0x000A.toUShort() to "tenth",
                0x000B.toUShort() to "eleventh",
                0x000C.toUShort() to "twelfth",
                0x000D.toUShort() to "thirteenth",
                0x000E.toUShort() to "fourteenth",
                0x000F.toUShort() to "fifteenth",
                0x0010.toUShort() to "sixteenth",
                0x0011.toUShort() to "seventeenth",
                0x0012.toUShort() to "eighteenth",
                0x0013.toUShort() to "nineteenth",
                0x0014.toUShort() to "twentieth",
                0x0015.toUShort() to "twenty first",
                0x0016.toUShort() to "twenty second",
                0x0017.toUShort() to "twenty third",
                0x0018.toUShort() to "twenty fourth",
                0x0019.toUShort() to "twenty fifth",
                0x001A.toUShort() to "twenty sixth",
                0x001B.toUShort() to "twenty seventh",
                0x001C.toUShort() to "twenty eighth",
                0x001D.toUShort() to "twenty ninth",
                0x001E.toUShort() to "thirtieth",
                0x001F.toUShort() to "thirty first",
                0x0020.toUShort() to "thirty second",
                0x0021.toUShort() to "thirty third",
                0x0022.toUShort() to "thirty fourth",
                0x0023.toUShort() to "thirty fifth",
                0x0024.toUShort() to "thirty sixth",
                0x0025.toUShort() to "thirty seventh",
                0x0026.toUShort() to "thirty eighth",
                0x0027.toUShort() to "thirty ninth",
                0x0028.toUShort() to "fortieth",
                0x0029.toUShort() to "forty first",
                0x002A.toUShort() to "forty second",
                0x002B.toUShort() to "forty third",
                0x002C.toUShort() to "forty fourth",
                0x002D.toUShort() to "forty fifth",
                0x002E.toUShort() to "forty sixth",
                0x002F.toUShort() to "forty seventh",
                0x0030.toUShort() to "forty eighth",
                0x0031.toUShort() to "forty ninth",
                0x0032.toUShort() to "fiftieth",
                0x0033.toUShort() to "fifty first",
                0x0034.toUShort() to "fifty second",
                0x0035.toUShort() to "fifty third",
                0x0036.toUShort() to "fifty fourth",
                0x0037.toUShort() to "fifty fifth",
                0x0038.toUShort() to "fifty sixth",
                0x0039.toUShort() to "fifty seventh",
                0x003A.toUShort() to "fifty eighth",
                0x003B.toUShort() to "fifty ninth",
                0x003C.toUShort() to "sixtieth",
                0x003D.toUShort() to "sixty first",
                0x003E.toUShort() to "sixty second",
                0x003F.toUShort() to "sixty third",
                0x0040.toUShort() to "sixty fourth",
                0x0041.toUShort() to "sixty fifth",
                0x0042.toUShort() to "sixty sixth",
                0x0043.toUShort() to "sixty seventh",
                0x0044.toUShort() to "sixty eighth",
                0x0045.toUShort() to "sixty ninth",
                0x0046.toUShort() to "seventieth",
                0x0047.toUShort() to "seventy first",
                0x0048.toUShort() to "seventy second",
                0x0049.toUShort() to "seventy third",
                0x004A.toUShort() to "seventy fourth",
                0x004B.toUShort() to "seventy fifth",
                0x004C.toUShort() to "seventy sixth",
                0x004D.toUShort() to "seventy seventh",
                0x004E.toUShort() to "seventy eighth",
                0x004F.toUShort() to "seventy ninth",
                0x0050.toUShort() to "eightieth",
                0x0051.toUShort() to "eighty first",
                0x0052.toUShort() to "eighty second",
                0x0053.toUShort() to "eighty third",
                0x0054.toUShort() to "eighty fourth",
                0x0055.toUShort() to "eighty fifth",
                0x0056.toUShort() to "eighty sixth",
                0x0057.toUShort() to "eighty seventh",
                0x0058.toUShort() to "eighty eighth",
                0x0059.toUShort() to "eighty ninth",
                0x005A.toUShort() to "ninetieth",
                0x005B.toUShort() to "ninety first",
                0x005C.toUShort() to "ninety second",
                0x005D.toUShort() to "ninety third",
                0x005E.toUShort() to "ninety fourth",
                0x005F.toUShort() to "ninety fifth",
                0x0060.toUShort() to "ninety sixth",
                0x0061.toUShort() to "ninety seventh",
                0x0062.toUShort() to "ninety eighth",
                0x0063.toUShort() to "ninety ninth",
                0x0064.toUShort() to "one hundredth",
                0x0065.toUShort() to "one hundred and first",
                0x0066.toUShort() to "one hundred and second",
                0x0067.toUShort() to "one hundred and third",
                0x0068.toUShort() to "one hundred and fourth",
                0x0069.toUShort() to "one hundred and fifth",
                0x006A.toUShort() to "one hundred and sixth",
                0x006B.toUShort() to "one hundred and seventh",
                0x006C.toUShort() to "one hundred and eighth",
                0x006D.toUShort() to "one hundred and ninth",
                0x006E.toUShort() to "one hundred and tenth",
                0x006F.toUShort() to "one hundred and eleventh",
                0x0070.toUShort() to "one hundred and twelfth",
                0x0071.toUShort() to "one hundred and thirteenth",
                0x0072.toUShort() to "one hundred and fourteenth",
                0x0073.toUShort() to "one hundred and fifteenth",
                0x0074.toUShort() to "one hundred and sixteenth",
                0x0075.toUShort() to "one hundred and seventeenth",
                0x0076.toUShort() to "one hundred and eighteenth",
                0x0077.toUShort() to "one hundred and nineteenth",
                0x0078.toUShort() to "one hundred twentieth",
                0x0079.toUShort() to "one hundred and twenty first",
                0x007A.toUShort() to "one hundred and twenty second",
                0x007B.toUShort() to "one hundred and twenty third",
                0x007C.toUShort() to "one hundred and twenty fourth",
                0x007D.toUShort() to "one hundred and twenty fifth",
                0x007E.toUShort() to "one hundred and twenty sixth",
                0x007F.toUShort() to "one hundred and twenty seventh",
                0x0080.toUShort() to "one hundred and twenty eighth",
                0x0081.toUShort() to "one hundred and twenty ninth",
                0x0082.toUShort() to "one hundred thirtieth",
                0x0083.toUShort() to "one hundred and thirty first",
                0x0084.toUShort() to "one hundred and thirty second",
                0x0085.toUShort() to "one hundred and thirty third",
                0x0086.toUShort() to "one hundred and thirty fourth",
                0x0087.toUShort() to "one hundred and thirty fifth",
                0x0088.toUShort() to "one hundred and thirty sixth",
                0x0089.toUShort() to "one hundred and thirty seventh",
                0x008A.toUShort() to "one hundred and thirty eighth",
                0x008B.toUShort() to "one hundred and thirty ninth",
                0x008C.toUShort() to "one hundred fortieth",
                0x008D.toUShort() to "one hundred and forty first",
                0x008E.toUShort() to "one hundred and forty second",
                0x008F.toUShort() to "one hundred and forty third",
                0x0090.toUShort() to "one hundred and forty fourth",
                0x0091.toUShort() to "one hundred and forty fifth",
                0x0092.toUShort() to "one hundred and forty sixth",
                0x0093.toUShort() to "one hundred and forty seventh",
                0x0094.toUShort() to "one hundred and forty eighth",
                0x0095.toUShort() to "one hundred and forty ninth",
                0x0096.toUShort() to "one hundred fiftieth",
                0x0097.toUShort() to "one hundred and fifty first",
                0x0098.toUShort() to "one hundred and fifty second",
                0x0099.toUShort() to "one hundred and fifty third",
                0x009A.toUShort() to "one hundred and fifty fourth",
                0x009B.toUShort() to "one hundred and fifty fifth",
                0x009C.toUShort() to "one hundred and fifty sixth",
                0x009D.toUShort() to "one hundred and fifty seventh",
                0x009E.toUShort() to "one hundred and fifty eighth",
                0x009F.toUShort() to "one hundred and fifty ninth",
                0x00A0.toUShort() to "one hundred sixtieth",
                0x00A1.toUShort() to "one hundred and sixty first",
                0x00A2.toUShort() to "one hundred and sixty second",
                0x00A3.toUShort() to "one hundred and sixty third",
                0x00A4.toUShort() to "one hundred and sixty fourth",
                0x00A5.toUShort() to "one hundred and sixty fifth",
                0x00A6.toUShort() to "one hundred and sixty sixth",
                0x00A7.toUShort() to "one hundred and sixty seventh",
                0x00A8.toUShort() to "one hundred and sixty eighth",
                0x00A9.toUShort() to "one hundred and sixty ninth",
                0x00AA.toUShort() to "one hundred seventieth",
                0x00AB.toUShort() to "one hundred and seventy first",
                0x00AC.toUShort() to "one hundred and seventy second",
                0x00AD.toUShort() to "one hundred and seventy third",
                0x00AE.toUShort() to "one hundred and seventy fourth",
                0x00AF.toUShort() to "one hundred and seventy fifth",
                0x00B0.toUShort() to "one hundred and seventy sixth",
                0x00B1.toUShort() to "one hundred and seventy seventh",
                0x00B2.toUShort() to "one hundred and seventy eighth",
                0x00B3.toUShort() to "one hundred and seventy ninth",
                0x00B4.toUShort() to "one hundred eightieth",
                0x00B5.toUShort() to "one hundred and eighty first",
                0x00B6.toUShort() to "one hundred and eighty second",
                0x00B7.toUShort() to "one hundred and eighty third",
                0x00B8.toUShort() to "one hundred and eighty fourth",
                0x00B9.toUShort() to "one hundred and eighty fifth",
                0x00BA.toUShort() to "one hundred and eighty sixth",
                0x00BB.toUShort() to "one hundred and eighty seventh",
                0x00BC.toUShort() to "one hundred and eighty eighth",
                0x00BD.toUShort() to "one hundred and eighty ninth",
                0x00BE.toUShort() to "one hundred ninetieth",
                0x00BF.toUShort() to "one hundred and ninety first",
                0x00C0.toUShort() to "one hundred and ninety second",
                0x00C1.toUShort() to "one hundred and ninety third",
                0x00C2.toUShort() to "one hundred and ninety fourth",
                0x00C3.toUShort() to "one hundred and ninety fifth",
                0x00C4.toUShort() to "one hundred and ninety sixth",
                0x00C5.toUShort() to "one hundred and ninety seventh",
                0x00C6.toUShort() to "one hundred and ninety eighth",
                0x00C7.toUShort() to "one hundred and ninety ninth",
                0x00C8.toUShort() to "two hundredth",
                0x00C9.toUShort() to "two hundred and first",
                0x00CA.toUShort() to "two hundred and second",
                0x00CB.toUShort() to "two hundred and third",
                0x00CC.toUShort() to "two hundred and fourth",
                0x00CD.toUShort() to "two hundred and fifth",
                0x00CE.toUShort() to "two hundred and sixth",
                0x00CF.toUShort() to "two hundred and seventh",
                0x00D0.toUShort() to "two hundred and eighth",
                0x00D1.toUShort() to "two hundred and ninth",
                0x00D2.toUShort() to "two hundred and tenth",
                0x00D3.toUShort() to "two hundred and eleventh",
                0x00D4.toUShort() to "two hundred and twelfth",
                0x00D5.toUShort() to "two hundred and thirteenth",
                0x00D6.toUShort() to "two hundred and fourteenth",
                0x00D7.toUShort() to "two hundred and fifteenth",
                0x00D8.toUShort() to "two hundred and sixteenth",
                0x00D9.toUShort() to "two hundred and seventeenth",
                0x00DA.toUShort() to "two hundred and eighteenth",
                0x00DB.toUShort() to "two hundred and nineteenth",
                0x00DC.toUShort() to "two hundred twentieth",
                0x00DD.toUShort() to "two hundred and twenty first",
                0x00DE.toUShort() to "two hundred and twenty second",
                0x00DF.toUShort() to "two hundred and twenty third",
                0x00E0.toUShort() to "two hundred and twenty fourth",
                0x00E1.toUShort() to "two hundred and twenty fifth",
                0x00E2.toUShort() to "two hundred and twenty sixth",
                0x00E3.toUShort() to "two hundred and twenty seventh",
                0x00E4.toUShort() to "two hundred and twenty eighth",
                0x00E5.toUShort() to "two hundred and twenty ninth",
                0x00E6.toUShort() to "two hundred thirtieth",
                0x00E7.toUShort() to "two hundred and thirty first",
                0x00E8.toUShort() to "two hundred and thirty second",
                0x00E9.toUShort() to "two hundred and thirty third",
                0x00EA.toUShort() to "two hundred and thirty fourth",
                0x00EB.toUShort() to "two hundred and thirty fifth",
                0x00EC.toUShort() to "two hundred and thirty sixth",
                0x00ED.toUShort() to "two hundred and thirty seventh",
                0x00EE.toUShort() to "two hundred and thirty eighth",
                0x00EF.toUShort() to "two hundred and thirty ninth",
                0x00F0.toUShort() to "two hundred fortieth",
                0x00F1.toUShort() to "two hundred and forty first",
                0x00F2.toUShort() to "two hundred and forty second",
                0x00F3.toUShort() to "two hundred and forty third",
                0x00F4.toUShort() to "two hundred and forty fourth",
                0x00F5.toUShort() to "two hundred and forty fifth",
                0x00F6.toUShort() to "two hundred and forty sixth",
                0x00F7.toUShort() to "two hundred and forty seventh",
                0x00F8.toUShort() to "two hundred and forty eighth",
                0x00F9.toUShort() to "two hundred and forty ninth",
                0x00FA.toUShort() to "two hundred fiftieth",
                0x00FB.toUShort() to "two hundred and fifty first",
                0x00FC.toUShort() to "two hundred and fifty second",
                0x00FD.toUShort() to "two hundred and fifty third",
                0x00FE.toUShort() to "two hundred and fifty fourth",
                0x00FF.toUShort() to "two hundred and fifty fifth",
                0x0100.toUShort() to "front",
                0x0101.toUShort() to "back",
                0x0102.toUShort() to "top",
                0x0103.toUShort() to "bottom",
                0x0104.toUShort() to "upper",
                0x0105.toUShort() to "lower",
                0x0106.toUShort() to "main",
                0x0107.toUShort() to "backup",
                0x0108.toUShort() to "auxiliary",
                0x0109.toUShort() to "supplementary",
                0x010A.toUShort() to "flash",
                0x010B.toUShort() to "inside",
                0x010C.toUShort() to "outside",
                0x010D.toUShort() to "left",
                0x010E.toUShort() to "right",
                0x010F.toUShort() to "internal",
                0x0110.toUShort() to "external"
            )
        }
    }
}
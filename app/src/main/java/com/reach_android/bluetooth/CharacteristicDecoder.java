package com.reach_android.bluetooth;

// Extracted from BluetoothGattCharacteristic.java

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class CharacteristicDecoder {
    /**
     * Characteristic value format type uint8
     */
    public static final int FORMAT_UINT8 = 0x11;
    /**
     * Characteristic value format type uint16
     */
    public static final int FORMAT_UINT16 = 0x12;
    /**
     * Characteristic value format type uint32
     */
    public static final int FORMAT_UINT32 = 0x14;
    /**
     * Characteristic value format type sint8
     */
    public static final int FORMAT_SINT8 = 0x21;
    /**
     * Characteristic value format type sint16
     */
    public static final int FORMAT_SINT16 = 0x22;
    /**
     * Characteristic value format type sint32
     */
    public static final int FORMAT_SINT32 = 0x24;
    /**
     * Characteristic value format type sfloat (16-bit float)
     */
    public static final int FORMAT_SFLOAT = 0x32;
    /**
     * Characteristic value format type float (32-bit float)
     */
    public static final int FORMAT_FLOAT = 0x34;


    public Integer getIntValue(byte[] mValue, int formatType, int offset) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;
        switch (formatType) {
            case FORMAT_UINT8:
                return unsignedByteToInt(mValue[offset]);
            case FORMAT_UINT16:
                return unsignedBytesToInt(mValue[offset], mValue[offset+1]);
            case FORMAT_UINT32:
                return unsignedBytesToInt(mValue[offset],   mValue[offset+1],
                        mValue[offset+2], mValue[offset+3]);
            case FORMAT_SINT8:
                return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8);
            case FORMAT_SINT16:
                return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                        mValue[offset+1]), 16);
            case FORMAT_SINT32:
                return unsignedToSigned(unsignedBytesToInt(mValue[offset],
                        mValue[offset+1], mValue[offset+2], mValue[offset+3]), 32);
        }
        return null;
    }

    public Float getFloatValue(byte[] mValue, int formatType, int offset) {
        if ((offset + getTypeLen(formatType)) > mValue.length) return null;
        switch (formatType) {
            case FORMAT_SFLOAT:
                return bytesToFloat(mValue[offset], mValue[offset+1]);
            case FORMAT_FLOAT:
                return bytesToFloat(mValue[offset],   mValue[offset+1],
                        mValue[offset+2], mValue[offset+3]);
        }
        return null;
    }

    public String getStringValue(byte[] mValue, int offset) {
        if (mValue == null || offset > mValue.length) return null;
        byte[] strBytes = new byte[mValue.length - offset];
        for (int i=0; i != (mValue.length-offset); ++i) strBytes[i] = mValue[offset+i];
        return new String(strBytes);
    }

    /**
     * Returns the size of a give value type.
     */
    private int getTypeLen(int formatType) {
        return formatType & 0xF;
    }
    /**
     * Convert a signed byte to an unsigned int.
     */
    private int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }
    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private int unsignedBytesToInt(byte b0, byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }
    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private int unsignedBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8))
                + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
    }
    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private float bytesToFloat(byte b0, byte b1) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + ((unsignedByteToInt(b1) & 0x0F) << 8), 12);
        int exponent = unsignedToSigned(unsignedByteToInt(b1) >> 4, 4);
        return (float)(mantissa * Math.pow(10, exponent));
    }
    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    private float bytesToFloat(byte b0, byte b1, byte b2, byte b3) {
        int mantissa = unsignedToSigned(unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) << 8)
                + (unsignedByteToInt(b2) << 16), 24);
        return (float)(mantissa * Math.pow(10, b3));
    }
    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << size-1)) != 0) {
            unsigned = -1 * ((1 << size-1) - (unsigned & ((1 << size-1) - 1)));
        }
        return unsigned;
    }
    /**
     * Convert an integer into the signed bits of a given length.
     */
    private int intToSignedBits(int i, int size) {
        if (i < 0) {
            i = (1 << size-1) + (i & ((1 << size-1) - 1));
        }
        return i;
    }
}

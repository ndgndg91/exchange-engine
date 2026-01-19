/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

@SuppressWarnings("all")
public enum ExecType
{
    Trade((short)1),

    Cancel((short)2),

    Reject((short)3),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((short)255);

    private final short value;

    ExecType(final short value)
    {
        this.value = value;
    }

    /**
     * The raw encoded value in the Java type representation.
     *
     * @return the raw value encoded.
     */
    public short value()
    {
        return value;
    }

    /**
     * Lookup the enum value representing the value.
     *
     * @param value encoded to be looked up.
     * @return the enum value representing the value.
     */
    public static ExecType get(final short value)
    {
        switch (value)
        {
            case 1: return Trade;
            case 2: return Cancel;
            case 3: return Reject;
            case 255: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}

/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

@SuppressWarnings("all")
public enum OrderType
{
    Limit((short)1),

    Market((short)2),

    StopLimit((short)3),

    StopMarket((short)4),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((short)255);

    private final short value;

    OrderType(final short value)
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
    public static OrderType get(final short value)
    {
        switch (value)
        {
            case 1: return Limit;
            case 2: return Market;
            case 3: return StopLimit;
            case 4: return StopMarket;
            case 255: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}

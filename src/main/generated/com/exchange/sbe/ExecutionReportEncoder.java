/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.MutableDirectBuffer;


/**
 * Trade execution details
 */
@SuppressWarnings("all")
public final class ExecutionReportEncoder
{
    public static final int BLOCK_LENGTH = 58;
    public static final int TEMPLATE_ID = 201;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ExecutionReportEncoder parentMessage = this;
    private MutableDirectBuffer buffer;
    private int initialOffset;
    private int offset;
    private int limit;

    public int sbeBlockLength()
    {
        return BLOCK_LENGTH;
    }

    public int sbeTemplateId()
    {
        return TEMPLATE_ID;
    }

    public int sbeSchemaId()
    {
        return SCHEMA_ID;
    }

    public int sbeSchemaVersion()
    {
        return SCHEMA_VERSION;
    }

    public String sbeSemanticType()
    {
        return "";
    }

    public MutableDirectBuffer buffer()
    {
        return buffer;
    }

    public int initialOffset()
    {
        return initialOffset;
    }

    public int offset()
    {
        return offset;
    }

    public ExecutionReportEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.initialOffset = offset;
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public ExecutionReportEncoder wrapAndApplyHeader(
        final MutableDirectBuffer buffer, final int offset, final MessageHeaderEncoder headerEncoder)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(BLOCK_LENGTH)
            .templateId(TEMPLATE_ID)
            .schemaId(SCHEMA_ID)
            .version(SCHEMA_VERSION);

        return wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH);
    }

    public int encodedLength()
    {
        return limit - offset;
    }

    public int limit()
    {
        return limit;
    }

    public void limit(final int limit)
    {
        this.limit = limit;
    }

    public static int matchIdId()
    {
        return 1;
    }

    public static int matchIdSinceVersion()
    {
        return 0;
    }

    public static int matchIdEncodingOffset()
    {
        return 0;
    }

    public static int matchIdEncodingLength()
    {
        return 8;
    }

    public static String matchIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long matchIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long matchIdMinValue()
    {
        return 0x0L;
    }

    public static long matchIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public ExecutionReportEncoder matchId(final long value)
    {
        buffer.putLong(offset + 0, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int makerOrderIdId()
    {
        return 2;
    }

    public static int makerOrderIdSinceVersion()
    {
        return 0;
    }

    public static int makerOrderIdEncodingOffset()
    {
        return 8;
    }

    public static int makerOrderIdEncodingLength()
    {
        return 8;
    }

    public static String makerOrderIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long makerOrderIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long makerOrderIdMinValue()
    {
        return 0x0L;
    }

    public static long makerOrderIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public ExecutionReportEncoder makerOrderId(final long value)
    {
        buffer.putLong(offset + 8, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int takerOrderIdId()
    {
        return 3;
    }

    public static int takerOrderIdSinceVersion()
    {
        return 0;
    }

    public static int takerOrderIdEncodingOffset()
    {
        return 16;
    }

    public static int takerOrderIdEncodingLength()
    {
        return 8;
    }

    public static String takerOrderIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long takerOrderIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long takerOrderIdMinValue()
    {
        return 0x0L;
    }

    public static long takerOrderIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public ExecutionReportEncoder takerOrderId(final long value)
    {
        buffer.putLong(offset + 16, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int makerUserIdId()
    {
        return 8;
    }

    public static int makerUserIdSinceVersion()
    {
        return 0;
    }

    public static int makerUserIdEncodingOffset()
    {
        return 24;
    }

    public static int makerUserIdEncodingLength()
    {
        return 8;
    }

    public static String makerUserIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long makerUserIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long makerUserIdMinValue()
    {
        return 0x0L;
    }

    public static long makerUserIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public ExecutionReportEncoder makerUserId(final long value)
    {
        buffer.putLong(offset + 24, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int takerUserIdId()
    {
        return 9;
    }

    public static int takerUserIdSinceVersion()
    {
        return 0;
    }

    public static int takerUserIdEncodingOffset()
    {
        return 32;
    }

    public static int takerUserIdEncodingLength()
    {
        return 8;
    }

    public static String takerUserIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long takerUserIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long takerUserIdMinValue()
    {
        return 0x0L;
    }

    public static long takerUserIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public ExecutionReportEncoder takerUserId(final long value)
    {
        buffer.putLong(offset + 32, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int priceId()
    {
        return 4;
    }

    public static int priceSinceVersion()
    {
        return 0;
    }

    public static int priceEncodingOffset()
    {
        return 40;
    }

    public static int priceEncodingLength()
    {
        return 8;
    }

    public static String priceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long priceNullValue()
    {
        return -9223372036854775808L;
    }

    public static long priceMinValue()
    {
        return -9223372036854775807L;
    }

    public static long priceMaxValue()
    {
        return 9223372036854775807L;
    }

    public ExecutionReportEncoder price(final long value)
    {
        buffer.putLong(offset + 40, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int qtyId()
    {
        return 5;
    }

    public static int qtySinceVersion()
    {
        return 0;
    }

    public static int qtyEncodingOffset()
    {
        return 48;
    }

    public static int qtyEncodingLength()
    {
        return 8;
    }

    public static String qtyMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long qtyNullValue()
    {
        return -9223372036854775808L;
    }

    public static long qtyMinValue()
    {
        return -9223372036854775807L;
    }

    public static long qtyMaxValue()
    {
        return 9223372036854775807L;
    }

    public ExecutionReportEncoder qty(final long value)
    {
        buffer.putLong(offset + 48, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int sideId()
    {
        return 6;
    }

    public static int sideSinceVersion()
    {
        return 0;
    }

    public static int sideEncodingOffset()
    {
        return 56;
    }

    public static int sideEncodingLength()
    {
        return 1;
    }

    public static String sideMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ExecutionReportEncoder side(final Side value)
    {
        buffer.putByte(offset + 56, (byte)value.value());
        return this;
    }

    public static int execTypeId()
    {
        return 7;
    }

    public static int execTypeSinceVersion()
    {
        return 0;
    }

    public static int execTypeEncodingOffset()
    {
        return 57;
    }

    public static int execTypeEncodingLength()
    {
        return 1;
    }

    public static String execTypeMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public ExecutionReportEncoder execType(final ExecType value)
    {
        buffer.putByte(offset + 57, (byte)value.value());
        return this;
    }

    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        return appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        if (null == buffer)
        {
            return builder;
        }

        final ExecutionReportDecoder decoder = new ExecutionReportDecoder();
        decoder.wrap(buffer, initialOffset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}

/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.MutableDirectBuffer;


/**
 * Cancel an existing order
 */
@SuppressWarnings("all")
public final class OrderCancelEncoder
{
    public static final int BLOCK_LENGTH = 28;
    public static final int TEMPLATE_ID = 101;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final OrderCancelEncoder parentMessage = this;
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

    public OrderCancelEncoder wrap(final MutableDirectBuffer buffer, final int offset)
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

    public OrderCancelEncoder wrapAndApplyHeader(
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

    public static int userIdId()
    {
        return 1;
    }

    public static int userIdSinceVersion()
    {
        return 0;
    }

    public static int userIdEncodingOffset()
    {
        return 0;
    }

    public static int userIdEncodingLength()
    {
        return 8;
    }

    public static String userIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long userIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long userIdMinValue()
    {
        return 0x0L;
    }

    public static long userIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public OrderCancelEncoder userId(final long value)
    {
        buffer.putLong(offset + 0, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int origOrderIdId()
    {
        return 2;
    }

    public static int origOrderIdSinceVersion()
    {
        return 0;
    }

    public static int origOrderIdEncodingOffset()
    {
        return 8;
    }

    public static int origOrderIdEncodingLength()
    {
        return 8;
    }

    public static String origOrderIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long origOrderIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long origOrderIdMinValue()
    {
        return 0x0L;
    }

    public static long origOrderIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public OrderCancelEncoder origOrderId(final long value)
    {
        buffer.putLong(offset + 8, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int symbolIdId()
    {
        return 3;
    }

    public static int symbolIdSinceVersion()
    {
        return 0;
    }

    public static int symbolIdEncodingOffset()
    {
        return 16;
    }

    public static int symbolIdEncodingLength()
    {
        return 4;
    }

    public static String symbolIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int symbolIdNullValue()
    {
        return -2147483648;
    }

    public static int symbolIdMinValue()
    {
        return -2147483647;
    }

    public static int symbolIdMaxValue()
    {
        return 2147483647;
    }

    public OrderCancelEncoder symbolId(final int value)
    {
        buffer.putInt(offset + 16, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int seqIdId()
    {
        return 4;
    }

    public static int seqIdSinceVersion()
    {
        return 0;
    }

    public static int seqIdEncodingOffset()
    {
        return 20;
    }

    public static int seqIdEncodingLength()
    {
        return 8;
    }

    public static String seqIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long seqIdNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long seqIdMinValue()
    {
        return 0x0L;
    }

    public static long seqIdMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public OrderCancelEncoder seqId(final long value)
    {
        buffer.putLong(offset + 20, value, java.nio.ByteOrder.LITTLE_ENDIAN);
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

        final OrderCancelDecoder decoder = new OrderCancelDecoder();
        decoder.wrap(buffer, initialOffset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}

/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.DirectBuffer;


/**
 * Trade execution details
 */
@SuppressWarnings("all")
public final class ExecutionReportDecoder
{
    public static final int BLOCK_LENGTH = 58;
    public static final int TEMPLATE_ID = 201;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final ExecutionReportDecoder parentMessage = this;
    private DirectBuffer buffer;
    private int initialOffset;
    private int offset;
    private int limit;
    int actingBlockLength;
    int actingVersion;

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

    public DirectBuffer buffer()
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

    public ExecutionReportDecoder wrap(
        final DirectBuffer buffer,
        final int offset,
        final int actingBlockLength,
        final int actingVersion)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.initialOffset = offset;
        this.offset = offset;
        this.actingBlockLength = actingBlockLength;
        this.actingVersion = actingVersion;
        limit(offset + actingBlockLength);

        return this;
    }

    public ExecutionReportDecoder wrapAndApplyHeader(
        final DirectBuffer buffer,
        final int offset,
        final MessageHeaderDecoder headerDecoder)
    {
        headerDecoder.wrap(buffer, offset);

        final int templateId = headerDecoder.templateId();
        if (TEMPLATE_ID != templateId)
        {
            throw new IllegalStateException("Invalid TEMPLATE_ID: " + templateId);
        }

        return wrap(
            buffer,
            offset + MessageHeaderDecoder.ENCODED_LENGTH,
            headerDecoder.blockLength(),
            headerDecoder.version());
    }

    public ExecutionReportDecoder sbeRewind()
    {
        return wrap(buffer, initialOffset, actingBlockLength, actingVersion);
    }

    public int sbeDecodedLength()
    {
        final int currentLimit = limit();
        sbeSkip();
        final int decodedLength = encodedLength();
        limit(currentLimit);

        return decodedLength;
    }

    public int actingVersion()
    {
        return actingVersion;
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

    public long matchId()
    {
        return buffer.getLong(offset + 0, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long makerOrderId()
    {
        return buffer.getLong(offset + 8, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long takerOrderId()
    {
        return buffer.getLong(offset + 16, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long makerUserId()
    {
        return buffer.getLong(offset + 24, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long takerUserId()
    {
        return buffer.getLong(offset + 32, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long price()
    {
        return buffer.getLong(offset + 40, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long qty()
    {
        return buffer.getLong(offset + 48, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public short sideRaw()
    {
        return ((short)(buffer.getByte(offset + 56) & 0xFF));
    }

    public Side side()
    {
        return Side.get(((short)(buffer.getByte(offset + 56) & 0xFF)));
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

    public short execTypeRaw()
    {
        return ((short)(buffer.getByte(offset + 57) & 0xFF));
    }

    public ExecType execType()
    {
        return ExecType.get(((short)(buffer.getByte(offset + 57) & 0xFF)));
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final ExecutionReportDecoder decoder = new ExecutionReportDecoder();
        decoder.wrap(buffer, initialOffset, actingBlockLength, actingVersion);

        return decoder.appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        if (null == buffer)
        {
            return builder;
        }

        final int originalLimit = limit();
        limit(initialOffset + actingBlockLength);
        builder.append("[ExecutionReport](sbeTemplateId=");
        builder.append(TEMPLATE_ID);
        builder.append("|sbeSchemaId=");
        builder.append(SCHEMA_ID);
        builder.append("|sbeSchemaVersion=");
        if (parentMessage.actingVersion != SCHEMA_VERSION)
        {
            builder.append(parentMessage.actingVersion);
            builder.append('/');
        }
        builder.append(SCHEMA_VERSION);
        builder.append("|sbeBlockLength=");
        if (actingBlockLength != BLOCK_LENGTH)
        {
            builder.append(actingBlockLength);
            builder.append('/');
        }
        builder.append(BLOCK_LENGTH);
        builder.append("):");
        builder.append("matchId=");
        builder.append(this.matchId());
        builder.append('|');
        builder.append("makerOrderId=");
        builder.append(this.makerOrderId());
        builder.append('|');
        builder.append("takerOrderId=");
        builder.append(this.takerOrderId());
        builder.append('|');
        builder.append("makerUserId=");
        builder.append(this.makerUserId());
        builder.append('|');
        builder.append("takerUserId=");
        builder.append(this.takerUserId());
        builder.append('|');
        builder.append("price=");
        builder.append(this.price());
        builder.append('|');
        builder.append("qty=");
        builder.append(this.qty());
        builder.append('|');
        builder.append("side=");
        builder.append(this.side());
        builder.append('|');
        builder.append("execType=");
        builder.append(this.execType());

        limit(originalLimit);

        return builder;
    }
    
    public ExecutionReportDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}

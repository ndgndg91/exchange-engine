/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.DirectBuffer;


/**
 * Market Data Snapshot (Top 5)
 */
@SuppressWarnings("all")
public final class OrderBookSnapshotDecoder
{
    public static final int BLOCK_LENGTH = 172;
    public static final int TEMPLATE_ID = 202;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final OrderBookSnapshotDecoder parentMessage = this;
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

    public OrderBookSnapshotDecoder wrap(
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

    public OrderBookSnapshotDecoder wrapAndApplyHeader(
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

    public OrderBookSnapshotDecoder sbeRewind()
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

    public static int symbolIdId()
    {
        return 1;
    }

    public static int symbolIdSinceVersion()
    {
        return 0;
    }

    public static int symbolIdEncodingOffset()
    {
        return 0;
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

    public int symbolId()
    {
        return buffer.getInt(offset + 0, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int seqIdId()
    {
        return 2;
    }

    public static int seqIdSinceVersion()
    {
        return 0;
    }

    public static int seqIdEncodingOffset()
    {
        return 4;
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

    public long seqId()
    {
        return buffer.getLong(offset + 4, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidPrice0Id()
    {
        return 10;
    }

    public static int bidPrice0SinceVersion()
    {
        return 0;
    }

    public static int bidPrice0EncodingOffset()
    {
        return 12;
    }

    public static int bidPrice0EncodingLength()
    {
        return 8;
    }

    public static String bidPrice0MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidPrice0NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidPrice0MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidPrice0MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidPrice0()
    {
        return buffer.getLong(offset + 12, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidQty0Id()
    {
        return 11;
    }

    public static int bidQty0SinceVersion()
    {
        return 0;
    }

    public static int bidQty0EncodingOffset()
    {
        return 20;
    }

    public static int bidQty0EncodingLength()
    {
        return 8;
    }

    public static String bidQty0MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidQty0NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidQty0MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidQty0MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidQty0()
    {
        return buffer.getLong(offset + 20, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidPrice1Id()
    {
        return 12;
    }

    public static int bidPrice1SinceVersion()
    {
        return 0;
    }

    public static int bidPrice1EncodingOffset()
    {
        return 28;
    }

    public static int bidPrice1EncodingLength()
    {
        return 8;
    }

    public static String bidPrice1MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidPrice1NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidPrice1MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidPrice1MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidPrice1()
    {
        return buffer.getLong(offset + 28, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidQty1Id()
    {
        return 13;
    }

    public static int bidQty1SinceVersion()
    {
        return 0;
    }

    public static int bidQty1EncodingOffset()
    {
        return 36;
    }

    public static int bidQty1EncodingLength()
    {
        return 8;
    }

    public static String bidQty1MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidQty1NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidQty1MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidQty1MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidQty1()
    {
        return buffer.getLong(offset + 36, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidPrice2Id()
    {
        return 14;
    }

    public static int bidPrice2SinceVersion()
    {
        return 0;
    }

    public static int bidPrice2EncodingOffset()
    {
        return 44;
    }

    public static int bidPrice2EncodingLength()
    {
        return 8;
    }

    public static String bidPrice2MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidPrice2NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidPrice2MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidPrice2MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidPrice2()
    {
        return buffer.getLong(offset + 44, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidQty2Id()
    {
        return 15;
    }

    public static int bidQty2SinceVersion()
    {
        return 0;
    }

    public static int bidQty2EncodingOffset()
    {
        return 52;
    }

    public static int bidQty2EncodingLength()
    {
        return 8;
    }

    public static String bidQty2MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidQty2NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidQty2MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidQty2MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidQty2()
    {
        return buffer.getLong(offset + 52, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidPrice3Id()
    {
        return 16;
    }

    public static int bidPrice3SinceVersion()
    {
        return 0;
    }

    public static int bidPrice3EncodingOffset()
    {
        return 60;
    }

    public static int bidPrice3EncodingLength()
    {
        return 8;
    }

    public static String bidPrice3MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidPrice3NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidPrice3MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidPrice3MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidPrice3()
    {
        return buffer.getLong(offset + 60, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidQty3Id()
    {
        return 17;
    }

    public static int bidQty3SinceVersion()
    {
        return 0;
    }

    public static int bidQty3EncodingOffset()
    {
        return 68;
    }

    public static int bidQty3EncodingLength()
    {
        return 8;
    }

    public static String bidQty3MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidQty3NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidQty3MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidQty3MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidQty3()
    {
        return buffer.getLong(offset + 68, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidPrice4Id()
    {
        return 18;
    }

    public static int bidPrice4SinceVersion()
    {
        return 0;
    }

    public static int bidPrice4EncodingOffset()
    {
        return 76;
    }

    public static int bidPrice4EncodingLength()
    {
        return 8;
    }

    public static String bidPrice4MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidPrice4NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidPrice4MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidPrice4MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidPrice4()
    {
        return buffer.getLong(offset + 76, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int bidQty4Id()
    {
        return 19;
    }

    public static int bidQty4SinceVersion()
    {
        return 0;
    }

    public static int bidQty4EncodingOffset()
    {
        return 84;
    }

    public static int bidQty4EncodingLength()
    {
        return 8;
    }

    public static String bidQty4MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long bidQty4NullValue()
    {
        return -9223372036854775808L;
    }

    public static long bidQty4MinValue()
    {
        return -9223372036854775807L;
    }

    public static long bidQty4MaxValue()
    {
        return 9223372036854775807L;
    }

    public long bidQty4()
    {
        return buffer.getLong(offset + 84, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askPrice0Id()
    {
        return 30;
    }

    public static int askPrice0SinceVersion()
    {
        return 0;
    }

    public static int askPrice0EncodingOffset()
    {
        return 92;
    }

    public static int askPrice0EncodingLength()
    {
        return 8;
    }

    public static String askPrice0MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askPrice0NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askPrice0MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askPrice0MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askPrice0()
    {
        return buffer.getLong(offset + 92, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askQty0Id()
    {
        return 31;
    }

    public static int askQty0SinceVersion()
    {
        return 0;
    }

    public static int askQty0EncodingOffset()
    {
        return 100;
    }

    public static int askQty0EncodingLength()
    {
        return 8;
    }

    public static String askQty0MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askQty0NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askQty0MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askQty0MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askQty0()
    {
        return buffer.getLong(offset + 100, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askPrice1Id()
    {
        return 32;
    }

    public static int askPrice1SinceVersion()
    {
        return 0;
    }

    public static int askPrice1EncodingOffset()
    {
        return 108;
    }

    public static int askPrice1EncodingLength()
    {
        return 8;
    }

    public static String askPrice1MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askPrice1NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askPrice1MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askPrice1MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askPrice1()
    {
        return buffer.getLong(offset + 108, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askQty1Id()
    {
        return 33;
    }

    public static int askQty1SinceVersion()
    {
        return 0;
    }

    public static int askQty1EncodingOffset()
    {
        return 116;
    }

    public static int askQty1EncodingLength()
    {
        return 8;
    }

    public static String askQty1MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askQty1NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askQty1MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askQty1MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askQty1()
    {
        return buffer.getLong(offset + 116, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askPrice2Id()
    {
        return 34;
    }

    public static int askPrice2SinceVersion()
    {
        return 0;
    }

    public static int askPrice2EncodingOffset()
    {
        return 124;
    }

    public static int askPrice2EncodingLength()
    {
        return 8;
    }

    public static String askPrice2MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askPrice2NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askPrice2MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askPrice2MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askPrice2()
    {
        return buffer.getLong(offset + 124, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askQty2Id()
    {
        return 35;
    }

    public static int askQty2SinceVersion()
    {
        return 0;
    }

    public static int askQty2EncodingOffset()
    {
        return 132;
    }

    public static int askQty2EncodingLength()
    {
        return 8;
    }

    public static String askQty2MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askQty2NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askQty2MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askQty2MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askQty2()
    {
        return buffer.getLong(offset + 132, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askPrice3Id()
    {
        return 36;
    }

    public static int askPrice3SinceVersion()
    {
        return 0;
    }

    public static int askPrice3EncodingOffset()
    {
        return 140;
    }

    public static int askPrice3EncodingLength()
    {
        return 8;
    }

    public static String askPrice3MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askPrice3NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askPrice3MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askPrice3MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askPrice3()
    {
        return buffer.getLong(offset + 140, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askQty3Id()
    {
        return 37;
    }

    public static int askQty3SinceVersion()
    {
        return 0;
    }

    public static int askQty3EncodingOffset()
    {
        return 148;
    }

    public static int askQty3EncodingLength()
    {
        return 8;
    }

    public static String askQty3MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askQty3NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askQty3MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askQty3MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askQty3()
    {
        return buffer.getLong(offset + 148, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askPrice4Id()
    {
        return 38;
    }

    public static int askPrice4SinceVersion()
    {
        return 0;
    }

    public static int askPrice4EncodingOffset()
    {
        return 156;
    }

    public static int askPrice4EncodingLength()
    {
        return 8;
    }

    public static String askPrice4MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askPrice4NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askPrice4MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askPrice4MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askPrice4()
    {
        return buffer.getLong(offset + 156, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int askQty4Id()
    {
        return 39;
    }

    public static int askQty4SinceVersion()
    {
        return 0;
    }

    public static int askQty4EncodingOffset()
    {
        return 164;
    }

    public static int askQty4EncodingLength()
    {
        return 8;
    }

    public static String askQty4MetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long askQty4NullValue()
    {
        return -9223372036854775808L;
    }

    public static long askQty4MinValue()
    {
        return -9223372036854775807L;
    }

    public static long askQty4MaxValue()
    {
        return 9223372036854775807L;
    }

    public long askQty4()
    {
        return buffer.getLong(offset + 164, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final OrderBookSnapshotDecoder decoder = new OrderBookSnapshotDecoder();
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
        builder.append("[OrderBookSnapshot](sbeTemplateId=");
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
        builder.append("symbolId=");
        builder.append(this.symbolId());
        builder.append('|');
        builder.append("seqId=");
        builder.append(this.seqId());
        builder.append('|');
        builder.append("bidPrice0=");
        builder.append(this.bidPrice0());
        builder.append('|');
        builder.append("bidQty0=");
        builder.append(this.bidQty0());
        builder.append('|');
        builder.append("bidPrice1=");
        builder.append(this.bidPrice1());
        builder.append('|');
        builder.append("bidQty1=");
        builder.append(this.bidQty1());
        builder.append('|');
        builder.append("bidPrice2=");
        builder.append(this.bidPrice2());
        builder.append('|');
        builder.append("bidQty2=");
        builder.append(this.bidQty2());
        builder.append('|');
        builder.append("bidPrice3=");
        builder.append(this.bidPrice3());
        builder.append('|');
        builder.append("bidQty3=");
        builder.append(this.bidQty3());
        builder.append('|');
        builder.append("bidPrice4=");
        builder.append(this.bidPrice4());
        builder.append('|');
        builder.append("bidQty4=");
        builder.append(this.bidQty4());
        builder.append('|');
        builder.append("askPrice0=");
        builder.append(this.askPrice0());
        builder.append('|');
        builder.append("askQty0=");
        builder.append(this.askQty0());
        builder.append('|');
        builder.append("askPrice1=");
        builder.append(this.askPrice1());
        builder.append('|');
        builder.append("askQty1=");
        builder.append(this.askQty1());
        builder.append('|');
        builder.append("askPrice2=");
        builder.append(this.askPrice2());
        builder.append('|');
        builder.append("askQty2=");
        builder.append(this.askQty2());
        builder.append('|');
        builder.append("askPrice3=");
        builder.append(this.askPrice3());
        builder.append('|');
        builder.append("askQty3=");
        builder.append(this.askQty3());
        builder.append('|');
        builder.append("askPrice4=");
        builder.append(this.askPrice4());
        builder.append('|');
        builder.append("askQty4=");
        builder.append(this.askQty4());

        limit(originalLimit);

        return builder;
    }
    
    public OrderBookSnapshotDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}

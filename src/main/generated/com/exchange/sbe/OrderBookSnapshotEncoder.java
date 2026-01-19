/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.MutableDirectBuffer;


/**
 * Market Data Snapshot (Top 5)
 */
@SuppressWarnings("all")
public final class OrderBookSnapshotEncoder
{
    public static final int BLOCK_LENGTH = 172;
    public static final int TEMPLATE_ID = 202;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final OrderBookSnapshotEncoder parentMessage = this;
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

    public OrderBookSnapshotEncoder wrap(final MutableDirectBuffer buffer, final int offset)
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

    public OrderBookSnapshotEncoder wrapAndApplyHeader(
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

    public OrderBookSnapshotEncoder symbolId(final int value)
    {
        buffer.putInt(offset + 0, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder seqId(final long value)
    {
        buffer.putLong(offset + 4, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidPrice0(final long value)
    {
        buffer.putLong(offset + 12, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidQty0(final long value)
    {
        buffer.putLong(offset + 20, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidPrice1(final long value)
    {
        buffer.putLong(offset + 28, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidQty1(final long value)
    {
        buffer.putLong(offset + 36, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidPrice2(final long value)
    {
        buffer.putLong(offset + 44, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidQty2(final long value)
    {
        buffer.putLong(offset + 52, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidPrice3(final long value)
    {
        buffer.putLong(offset + 60, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidQty3(final long value)
    {
        buffer.putLong(offset + 68, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidPrice4(final long value)
    {
        buffer.putLong(offset + 76, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder bidQty4(final long value)
    {
        buffer.putLong(offset + 84, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askPrice0(final long value)
    {
        buffer.putLong(offset + 92, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askQty0(final long value)
    {
        buffer.putLong(offset + 100, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askPrice1(final long value)
    {
        buffer.putLong(offset + 108, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askQty1(final long value)
    {
        buffer.putLong(offset + 116, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askPrice2(final long value)
    {
        buffer.putLong(offset + 124, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askQty2(final long value)
    {
        buffer.putLong(offset + 132, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askPrice3(final long value)
    {
        buffer.putLong(offset + 140, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askQty3(final long value)
    {
        buffer.putLong(offset + 148, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askPrice4(final long value)
    {
        buffer.putLong(offset + 156, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
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

    public OrderBookSnapshotEncoder askQty4(final long value)
    {
        buffer.putLong(offset + 164, value, java.nio.ByteOrder.LITTLE_ENDIAN);
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

        final OrderBookSnapshotDecoder decoder = new OrderBookSnapshotDecoder();
        decoder.wrap(buffer, initialOffset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}

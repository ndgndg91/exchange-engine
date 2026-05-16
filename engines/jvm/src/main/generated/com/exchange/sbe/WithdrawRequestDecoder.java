/* Generated SBE (Simple Binary Encoding) message codec. */
package com.exchange.sbe;

import org.agrona.DirectBuffer;


/**
 * External Withdraw Request
 */
@SuppressWarnings("all")
public final class WithdrawRequestDecoder
{
    public static final int BLOCK_LENGTH = 28;
    public static final int TEMPLATE_ID = 111;
    public static final int SCHEMA_ID = 1;
    public static final int SCHEMA_VERSION = 0;
    public static final String SEMANTIC_VERSION = "1.0";
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final WithdrawRequestDecoder parentMessage = this;
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

    public WithdrawRequestDecoder wrap(
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

    public WithdrawRequestDecoder wrapAndApplyHeader(
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

    public WithdrawRequestDecoder sbeRewind()
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

    public long userId()
    {
        return buffer.getLong(offset + 0, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int currencyIdId()
    {
        return 2;
    }

    public static int currencyIdSinceVersion()
    {
        return 0;
    }

    public static int currencyIdEncodingOffset()
    {
        return 8;
    }

    public static int currencyIdEncodingLength()
    {
        return 4;
    }

    public static String currencyIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int currencyIdNullValue()
    {
        return -2147483648;
    }

    public static int currencyIdMinValue()
    {
        return -2147483647;
    }

    public static int currencyIdMaxValue()
    {
        return 2147483647;
    }

    public int currencyId()
    {
        return buffer.getInt(offset + 8, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public static int amountId()
    {
        return 3;
    }

    public static int amountSinceVersion()
    {
        return 0;
    }

    public static int amountEncodingOffset()
    {
        return 12;
    }

    public static int amountEncodingLength()
    {
        return 8;
    }

    public static String amountMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long amountNullValue()
    {
        return -9223372036854775808L;
    }

    public static long amountMinValue()
    {
        return -9223372036854775807L;
    }

    public static long amountMaxValue()
    {
        return 9223372036854775807L;
    }

    public long amount()
    {
        return buffer.getLong(offset + 12, java.nio.ByteOrder.LITTLE_ENDIAN);
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

    public long seqId()
    {
        return buffer.getLong(offset + 20, java.nio.ByteOrder.LITTLE_ENDIAN);
    }


    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        final WithdrawRequestDecoder decoder = new WithdrawRequestDecoder();
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
        builder.append("[WithdrawRequest](sbeTemplateId=");
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
        builder.append("userId=");
        builder.append(this.userId());
        builder.append('|');
        builder.append("currencyId=");
        builder.append(this.currencyId());
        builder.append('|');
        builder.append("amount=");
        builder.append(this.amount());
        builder.append('|');
        builder.append("seqId=");
        builder.append(this.seqId());

        limit(originalLimit);

        return builder;
    }
    
    public WithdrawRequestDecoder sbeSkip()
    {
        sbeRewind();

        return this;
    }
}

//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2.parser;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http2.frames.PingFrame;

public class PingBodyParser extends BodyParser
{
    private State state = State.PREPARE;
    private int cursor;
    private byte[] payload;

    public PingBodyParser(HeaderParser headerParser, Parser.Listener listener)
    {
        super(headerParser, listener);
    }

    private void reset()
    {
        state = State.PREPARE;
        cursor = 0;
        payload = null;
    }

    @Override
    public Result parse(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            switch (state)
            {
                case PREPARE:
                {
                    int length = getBodyLength();
                    if (length != 8)
                    {
                        return notifyConnectionFailure(ErrorCode.PROTOCOL_ERROR, "invalid_ping_frame");
                    }
                    state = State.PAYLOAD;
                    break;
                }
                case PAYLOAD:
                {
                    payload = new byte[8];
                    if (buffer.remaining() >= 8)
                    {
                        buffer.get(payload);
                        return onPing(payload);
                    }
                    else
                    {
                        state = State.PAYLOAD_BYTES;
                        cursor = 8;
                    }
                    break;
                }
                case PAYLOAD_BYTES:
                {
                    payload[8 - cursor] = buffer.get();
                    --cursor;
                    if (cursor == 0)
                    {
                        return onPing(payload);
                    }
                    break;
                }
                default:
                {
                    throw new IllegalStateException();
                }
            }
        }
        return Result.PENDING;
    }

    private Result onPing(byte[] payload)
    {
        PingFrame frame = new PingFrame(payload, hasFlag(0x1));
        reset();
        return notifyPing(frame) ? Result.ASYNC : Result.COMPLETE;
    }

    private enum State
    {
        PREPARE, PAYLOAD, PAYLOAD_BYTES
    }
}

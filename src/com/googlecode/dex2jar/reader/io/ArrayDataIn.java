/*
 * Copyright (c) 2009-2012 Panxiaobo
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
package com.googlecode.dex2jar.reader.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Stack;

/**
 * 
 * @author <a href="mailto:pxb1988@gmail.com">Panxiaobo</a>
 * @version $Rev: 9fd8005bbaa4 $
 */
public abstract class ArrayDataIn extends ByteArrayInputStream implements DataIn {

    private Stack<Integer> stack = new Stack<Integer>();

    public ArrayDataIn(byte[] data) {
        super(data);
    }

    @Override
    public int readShortx() {
        return (short) readUShortx();
    }

    @Override
    public int readIntx() {
        return readUIntx();
    }

    @Override
    public int getCurrentPosition() {
        return super.pos;
    }

    @Override
    public void move(int absOffset) {
        super.pos = absOffset;
    }

    @Override
    public void pop() {
        super.pos = stack.pop();
    }

    @Override
    public void push() {
        stack.push(super.pos);
    }

    @Override
    public void pushMove(int absOffset) {
        this.push();
        this.move(absOffset);
    }

    @Override
    public byte[] readBytes(int size) {
        byte[] data = new byte[size];
        try {
            super.read(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return data;
    }

    @Override
    public long readLeb128() {
        int bitpos = 0;
        long vln = 0L;
        do {
            int inp = readUByte();
            vln |= ((long) (inp & 0x7F)) << bitpos;
            bitpos += 7;
            if ((inp & 0x80) == 0) {
                break;
            }
        } while (true);
        if (((1L << (bitpos - 1)) & vln) != 0) {
            vln -= (1L << bitpos);
        }
        return vln;
    }

    @Override
    public long readULeb128() {
        long value = 0;
        int count = 0;
        int b = readUByte();
        while ((b & 0x80) != 0) {
            value |= (b & 0x7f) << count;
            count += 7;
            b = readUByte();
        }
        value |= (b & 0x7f) << count;
        return value;
    }

    @Override
    public void skip(int bytes) {
        super.skip(bytes);
    }

    @Override
    public int readByte() {
        return (byte) readUByte();
    }

    @Override
    public int readUByte() {
        if (super.pos >= super.count) {
            throw new RuntimeException("EOF");
        }
        return super.read();
    }
}

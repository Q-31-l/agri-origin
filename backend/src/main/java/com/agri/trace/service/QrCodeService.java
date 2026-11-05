package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * 溯源二维码生成：内容为 H5 溯源页地址，扫一扫即可直达。
 *
 * <p>注意：运行环境为精简版 JDK（无 awt/imageio 原生库），
 * 因此这里不使用 {@code BufferedImage} + {@code ImageIO}，
 * 而是直接把 zxing 的 {@link BitMatrix} 编码为灰度 PNG 字节流，
 * 仅依赖 JDK 自带的 {@code java.util.zip}，可在任何 headless 环境运行。</p>
 */
@Service
public class QrCodeService {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    /** 溯源页地址前缀，如 http://host/pages/trace/index?code= */
    @Value("${app.trace-url-prefix:http://localhost:5173/pages/trace/index?code=}")
    private String traceUrlPrefix;

    public byte[] qrCodePng(String traceCode, int size) {
        if (traceCode == null || traceCode.trim().isEmpty()) {
            throw BizException.badRequest("溯源码不能为空");
        }
        int px = Math.max(120, Math.min(size, 1024));
        String content = traceUrlPrefix + traceCode;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints);
            return encodeGrayPng(matrix);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "二维码生成失败：" + e.getMessage());
        }
    }

    /**
     * 把 BitMatrix 编码为 8 位灰度 PNG（位深 8，颜色类型 0）。
     * 黑色模块写入 0，白色写入 255。
     */
    private byte[] encodeGrayPng(BitMatrix matrix) throws Exception {
        int width = matrix.getWidth();
        int height = matrix.getHeight();

        // 每行：1 字节 filter(0) + width 字节灰度
        byte[] raw = new byte[height * (width + 1)];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            raw[idx++] = 0;
            for (int x = 0; x < width; x++) {
                raw[idx++] = matrix.get(x, y) ? (byte) 0x00 : (byte) 0xFF;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 1024);
        out.write(PNG_SIGNATURE);

        // IHDR
        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, width);
        writeInt(ihdr, 4, height);
        ihdr[8] = 8;   // bit depth
        ihdr[9] = 0;   // color type: grayscale
        ihdr[10] = 0;  // compression
        ihdr[11] = 0;  // filter
        ihdr[12] = 0;  // interlace
        writeChunk(out, "IHDR", ihdr);

        // IDAT
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 4 + 256);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(compressed, deflater)) {
            dos.write(raw);
        } finally {
            deflater.end();
        }
        writeChunk(out, "IDAT", compressed.toByteArray());

        // IEND
        writeChunk(out, "IEND", new byte[0]);

        return out.toByteArray();
    }

    private void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes("US-ASCII");
        byte[] len = new byte[4];
        writeInt(len, 0, data.length);
        out.write(len);
        out.write(typeBytes);
        out.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeInt(crcBytes, 0, (int) crc.getValue());
        out.write(crcBytes);
    }

    private void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) ((value >>> 24) & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        buf[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        buf[offset + 3] = (byte) (value & 0xFF);
    }
}

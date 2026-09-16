package com.oskott.dogtrainerbackend.storage;

import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Decodes uploaded images (jpg/png/webp) and re-encodes them as JPEG, downscaling (never
 * upscaling) to fit within a target bounding box. Used to shrink avatars, dog photos and post
 * images before they're stored in R2, saving space.
 */
@Service
public class ImageProcessingService {

    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Target bounding box for avatar and dog photos: fit within 400x400, keep aspect ratio.
     */
    public static final int AVATAR_DOG_MAX_DIMENSION = 400;

    /**
     * Target bounding box for post images: fit within 1080x1080 (Instagram-style, longest side
     * capped at 1080px), keep aspect ratio.
     */
    public static final int POST_MAX_DIMENSION = 1080;

    public byte[] resizeToJpeg(byte[] input, int maxWidth, int maxHeight) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException e) {
            throw new InvalidFileException("Could not read image: " + e.getMessage());
        }
        if (source == null) {
            throw new InvalidFileException("Unsupported or corrupt image file");
        }

        int targetWidth = Math.min(maxWidth, source.getWidth());
        int targetHeight = Math.min(maxHeight, source.getHeight());

        try {
            BufferedImage flattened = flattenTransparency(source);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(flattened)
                    .size(targetWidth, targetHeight)
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidFileException("Could not process image: " + e.getMessage());
        }
    }

    /**
     * JPEG has no alpha channel; transparent pixels (from PNG/WebP) are flattened onto a white
     * background so the JPEG encoder doesn't produce corrupted colors.
     */
    private BufferedImage flattenTransparency(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage flattened = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flattened;
    }
}

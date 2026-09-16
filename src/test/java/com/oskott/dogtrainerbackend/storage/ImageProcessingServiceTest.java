package com.oskott.dogtrainerbackend.storage;

import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingServiceTest {

    private final ImageProcessingService imageProcessingService = new ImageProcessingService();

    @Test
    void resizeToJpegDownscalesLargeImageToFitBoundingBoxPreservingAspectRatio() throws IOException {
        byte[] input = encodePng(newImage(800, 600, BufferedImage.TYPE_INT_RGB, Color.BLUE));

        byte[] output = imageProcessingService.resizeToJpeg(input, 400, 400);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(result.getWidth()).isLessThanOrEqualTo(400);
        assertThat(result.getHeight()).isLessThanOrEqualTo(400);
        // Original aspect ratio (4:3) preserved.
        assertThat(result.getWidth()).isEqualTo(400);
        assertThat(result.getHeight()).isEqualTo(300);
    }

    @Test
    void resizeToJpegDoesNotUpscaleImagesSmallerThanTheBoundingBox() throws IOException {
        byte[] input = encodePng(newImage(100, 80, BufferedImage.TYPE_INT_RGB, Color.RED));

        byte[] output = imageProcessingService.resizeToJpeg(input, 400, 400);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(80);
    }

    @Test
    void resizeToJpegAlwaysProducesJpegOutputRegardlessOfInputFormat() throws IOException {
        byte[] pngInput = encodePng(newImage(200, 200, BufferedImage.TYPE_INT_RGB, Color.GREEN));

        byte[] output = imageProcessingService.resizeToJpeg(pngInput, 400, 400);

        // JPEG magic bytes: FF D8 FF
        assertThat(output[0]).isEqualTo((byte) 0xFF);
        assertThat(output[1]).isEqualTo((byte) 0xD8);
        assertThat(output[2]).isEqualTo((byte) 0xFF);
    }

    @Test
    void resizeToJpegFlattensTransparentPixelsOntoWhiteBackground() throws IOException {
        byte[] transparentPng = encodePng(newImage(200, 200, BufferedImage.TYPE_INT_ARGB, new Color(0, 0, 0, 0)));

        byte[] output = imageProcessingService.resizeToJpeg(transparentPng, 400, 400);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(result.getColorModel().hasAlpha()).isFalse();
        int rgb = result.getRGB(100, 100) & 0xFFFFFF;
        assertThat(rgb).isEqualTo(0xFFFFFF);
    }

    @Test
    void resizeToJpegThrowsInvalidFileExceptionForUnreadableBytes() {
        byte[] garbage = {1, 2, 3, 4, 5};

        assertThatThrownBy(() -> imageProcessingService.resizeToJpeg(garbage, 400, 400))
                .isInstanceOf(InvalidFileException.class);
    }

    private static BufferedImage newImage(int width, int height, int type, Color fillColor) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(fillColor);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}

package com.forbidad4tieba.hook.feature.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SystemSplashArtworkTest {
    @Test fun bundledArtworkHasTheExpectedCanvasAndBlendsIntoTheSplashBackground() {
        val root = File(checkNotNull(System.getProperty("project.root")))
        val asset = File(root, "features/src/main/${SystemSplashArtwork.ASSET_PATH}")
        val image = ImageIO.read(asset)
        assertNotNull("The splash asset must be a decodable image", image)
        assertEquals(1080, image.width)
        assertEquals(360, image.height)
        // A mismatched opaque edge would reintroduce the visible bottom rectangle.
        for (x in 0 until image.width) {
            assertEquals(SystemSplashPolicy.DARK_BACKGROUND, image.getRGB(x, 0))
            assertEquals(SystemSplashPolicy.DARK_BACKGROUND, image.getRGB(x, image.height - 1))
        }
        for (y in 0 until image.height) {
            assertEquals(SystemSplashPolicy.DARK_BACKGROUND, image.getRGB(0, y))
            assertEquals(SystemSplashPolicy.DARK_BACKGROUND, image.getRGB(image.width - 1, y))
        }
    }
}

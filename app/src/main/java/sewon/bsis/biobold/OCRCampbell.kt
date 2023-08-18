package sewon.bsis.biobold

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader


class OCRCampbell(private var bitmap: Bitmap, private val ctx: Context) {
    private val TAG = "biobold_OCRCampbell"
    private val tess: TessBaseAPI = TessBaseAPI()
    private val COMMON_SYMS = arrayOf('.', ',', '?', '!', '&', '(', ')', '-', '~', ':', "'", '"', '\n', '*')

    private val am: AssetManager = ctx.resources.assets

    private var wordsSet: Set<String>
    private var module: Module

    private val modifiedMat = Mat()

    private fun isNoise(str: String): Boolean {
        for(c in str) {
            if(!c.isLetterOrDigit() || !COMMON_SYMS.contains(c)) {
                return false
            }
        }
        return true
    }

    private fun puncClear(s: String): Triple<String, String, String> {
        // Ad Hoc...
        var pre = ""
        var pos = ""
        var newS = s
        if (newS.length == 1) {
            return Triple(newS, pre, pos)
        }
        if (!newS[0].isLetter()) {
            pre = newS[0].toString()
            newS = newS.substring(1)
        }
        if (newS.length == 1) {
            return Triple(newS, pre, pos)
        }
        if (!newS.last().isLetter()) {
            pos = newS.last().toString()
            newS = newS.dropLast(1)
        }
        return Triple(newS, pre, pos)
    }
    private fun performOCR(): MutableList<Array<Any>> {
        val resultIterator = tess.resultIterator
        val data: MutableList<Array<Any>> = ArrayList()
//        val boxes: MutableList<Rect> = ArrayList()
//        val texts: MutableList<String> = ArrayList()

        while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD)) {
            val rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            val text = resultIterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            if(text.isNotBlank()) {
                data.add(arrayOf(
                    rect.left,
                    rect.top,
                    rect.right - rect.left,
                    rect.bottom - rect.top,
                    text
                ))
            }
        }
        return data
    }

    @Throws(IOException::class)
    private fun assetFilePath(assetName: String): String? {
        val file = File(ctx.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        ctx.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }

    private fun isBold(inputMat: Mat): Boolean {
        val inputBitmap: Bitmap? = null
        Utils.matToBitmap(inputMat, inputBitmap)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(inputBitmap, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB)
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray

        var maxScore = -Float.MAX_VALUE
        var maxScoreIdx = -1
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxScoreIdx = i
            }
        }

        return when(maxScoreIdx) {
            0 -> false
            1 -> true
            else -> false
        }
    }

    init {
        module = Module.load(assetFilePath("cnn_model.ptl"))

        val iStream = am.open("words.txt")
        val bReader = BufferedReader(InputStreamReader(iStream))
        wordsSet = bReader.readLines().toSet()

        Utils.bitmapToMat(bitmap, modifiedMat)
        val newWidth = 1000.0
        val newHeight = (newWidth * bitmap.height) / bitmap.width
        Imgproc.resize(modifiedMat, modifiedMat, Size(newWidth, newHeight))
        Imgproc.adaptiveThreshold(modifiedMat, modifiedMat,
            255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 25, 15.0
        )
        Utils.matToBitmap(modifiedMat, bitmap)
        tess.setImage(bitmap)

        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
        tess.utF8Text
        if(OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV Init succeded")
        } else {
            Log.e(TAG, "OpenCV Init failed")
        }
    }

    fun getCorrectedText() {
        val ocrResult = performOCR()
        val refinedOcrResult = ocrResult.filter { !isNoise(it[4] as String) }

        val lowerWordsSet = wordsSet.map { it.lowercase() }.toSet()
        var correctedText = ""

        for(tup in refinedOcrResult) {
            val word = tup[4] as String
            val left = tup[0] as Int
            val top = tup[1] as Int
            val width = tup[2] as Int
            val height = tup[3] as Int

            val right = left + width
            val bottom = top + height

            val bold = isBold(modifiedMat.submat(Rect(left, top, width, height)))

            if(COMMON_SYMS.contains(word[0])) {
                correctedText += "$word ";
                continue
            }

            var (cleanWord, pre, pos) = puncClear(word)
            if(lowerWordsSet.contains(cleanWord.lowercase())) {
                correctedText += if(bold)
                    "**$word** "
                else
                    "$word "
            } else {
                // TODO
            }
        }
    }
}
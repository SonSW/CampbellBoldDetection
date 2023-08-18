package sewon.bsis.biobold

import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI


class OCRCampbell(bitmap: Bitmap) {
    private val tess: TessBaseAPI = TessBaseAPI()

    init {
        tess.setImage(bitmap)
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD


    }

    fun get_boxes() {
        tess.utF8Text

        val resultIterator = tess.resultIterator
        val boxes: MutableList<Rect> = ArrayList()
        val texts: MutableList<String> = ArrayList()

        while (resultIterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD)) {
            val rect = resultIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            val text = resultIterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            boxes.add(rect)
            texts.add(text)
        }
    }
}
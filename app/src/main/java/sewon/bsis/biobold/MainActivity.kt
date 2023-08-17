@file:Suppress("DEPRECATION")

package sewon.bsis.biobold

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class MainActivity : AppCompatActivity() {
    private val CAMERA_CAPTURE_CODE = 200
    private val TAG = "biobold"

    private lateinit var button: Button
    private lateinit var imageView: ImageView

    private var photoUri: Uri? = null
    private var imageFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndGrantPermissions(arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ))

        // val tess = TessBaseAPI()
        button = findViewById(R.id.button)
        imageView = findViewById(R.id.imageView)

        button.setOnClickListener {
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile = createImageFile()
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider", photoFile
                )
                i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                startActivityForResult(i, CAMERA_CAPTURE_CODE)
            }
        }
    }

    private fun checkAndGrantPermissions(perms: Array<String>) {
        val permList = mutableListOf<String>()
        for(perm in perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permList.add(perm)
            }
        }
        ActivityCompat.requestPermissions(this, permList.toTypedArray(), 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_CAPTURE_CODE && resultCode == RESULT_OK) {
            Toast.makeText(this, "hooray!", Toast.LENGTH_LONG).show()
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, photoUri!!))
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
            }
            val exif = ExifInterface(imageFilePath!!)

            val exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            val exifDegree = exifOrientationToDegrees(exifOrientation);

            // 이미지를 출력
            imageView.setImageBitmap(rotate(bitmap, exifDegree.toFloat()));
        }
    }

    private fun exifOrientationToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    // 이미지를 회전시키는 메서드 선언
    private fun rotate(bitmap: Bitmap, degree: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun createImageFile(): File? {
        // 파일이름을 세팅 및 저장경로 세팅
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val imageFileName = "JPEG_" + timeStamp + "_"
            val storageDir =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            )
            imageFilePath = image.absolutePath
            image
        } catch (e: IOException) {
            null
        }
    }
}
package com.example.drawy

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    var customProgressDialog : Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result ->
            if(result.resultCode == RESULT_OK && result.data!=null){
                val imageBackGround: ImageView = findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission Granted now you can read the storage files.",
                        Toast.LENGTH_LONG
                    ).show()
                }else {
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            this@MainActivity,
                            "Oops You just Denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(15.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton

        val ib_undo : ImageButton = findViewById(R.id.ib_undo)
        ib_undo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ib_gallery : ImageButton = findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener {
            requestStoragePermission()
        }

        val ib_save : ImageButton = findViewById(R.id.ib_save)
        ib_save.setOnClickListener {
            showProgressDialog()
            lifecycleScope.launch {
                val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                saveBitmapFile(getBitmapFromView(flDrawingView))
            }
        }

        val ib_brush : ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ib_clear : ImageButton = findViewById(R.id.ib_clear)
        ib_clear.setOnClickListener {
            drawingView?.clearCanvas()
        }



    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(15.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(25.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if(view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view

        }
    }

//    private fun isReadStorageAllowed() : Boolean {
//        val result = ContextCompat.checkSelfPermission(this,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//        return result == PackageManager.PERMISSION_GRANTED
//    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Permission already granted
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            } else {
                // Permission not granted, request it
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "Please grant permission to access external storage.", Toast.LENGTH_LONG).show()
            }
        } else {
            // Android 10 or lower, request the permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                showRationalDialog("Drawing App", "Drawing App needs to Access your External Storage")
            } else {
                requestPermission.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

    }

    private fun showRationalDialog(
        title: String,
        message: String
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") {dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,
            view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null) bgDrawable.draw(canvas)
        else canvas.drawColor(Color.WHITE)

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?) : String {
        var result = " "
        withContext(Dispatchers.IO) {
            if(mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString()
                              + File.separator + "Drawy_" + System.currentTimeMillis() / 1000 + ".png"
                    )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if(result.isNotEmpty()) {
                           Toast.makeText(
                               this@MainActivity,
                               "File Saved Successfully at : $result",
                               Toast.LENGTH_SHORT
                           ).show()
                            shareImage(FileProvider.getUriForFile(baseContext, "com.example.drawy.fileprovider", f))
                        }else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something Went Wrong",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                catch (e: Exception) {
                    result = " "
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog() {
        if(customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(uri: Uri) {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
}
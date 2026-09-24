package id.local.shopeeextractor

import android.app.Application
import id.local.shopeeextractor.data.AppDatabase
import id.local.shopeeextractor.data.CaptureRepository
import id.local.shopeeextractor.session.CaptureCoordinator

class ShopeeExtractorApp : Application() {
    val repository by lazy { CaptureRepository(AppDatabase.get(this).captureDao()) }
    val coordinator by lazy { CaptureCoordinator(repository) }
}

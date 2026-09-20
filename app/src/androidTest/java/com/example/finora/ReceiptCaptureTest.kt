package com.example.finora

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.finora.data.db.entities.Account
import com.example.finora.data.db.entities.AccountType
import com.example.finora.data.db.entities.Category
import com.example.finora.data.db.entities.Transaction
import com.example.finora.ui.transactions.AddEditTransactionActivity
import com.example.finora.ui.transactions.TransactionDetailActivity
import com.example.finora.ui.transactions.TransactionsViewModel
import com.example.finora.util.ImageUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ReceiptCaptureTest {

    private lateinit var app: FinoraApp
    private lateinit var viewModel: TransactionsViewModel
    private lateinit var context: Context

    @Before
    fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        app = context as FinoraApp
        viewModel = TransactionsViewModel(
            app.transactionRepository,
            app.accountRepository,
            app.categoryRepository
        )

        // Clean out existing transactions and accounts
        val transactions = app.transactionRepository.allTransactions.first()
        transactions.forEach { app.transactionRepository.delete(it) }

        val accounts = app.accountRepository.allAccounts.first()
        accounts.forEach { app.accountRepository.delete(it) }

        // Seed category if needed
        val catCount = app.categoryRepository.getCount()
        if (catCount == 0) {
            app.categoryRepository.insert(Category(name = "Groceries", type = "EXPENSE"))
        }
    }

    /**
     * Confirms that creating/saving a transaction without a receipt
     * (e.g. if the user skips receipt scanning or camera permission is denied)
     * does NOT crash the app and the transaction is successfully saved to Room.
     */
    @Test
    fun testSaveTransactionWithoutReceipt_doesNotCrashAndSavesSuccessfully(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Everyday Cash", type = AccountType.CASH.storageValue, balance = 500.0)
        ).toInt()

        ActivityScenario.launch(AddEditTransactionActivity::class.java).use {
            // Verify receipt scan button is displayed and empty preview is shown
            onView(withId(R.id.btn_scan_receipt)).check(matches(isDisplayed()))
            onView(withId(R.id.layout_receipt_preview)).check(matches(not(isDisplayed())))

            // Fill form fields without attaching receipt
            onView(withId(R.id.et_amount)).perform(typeText("42.50"), closeSoftKeyboard())
            onView(withId(R.id.et_merchant)).perform(typeText("Local Bakery"), closeSoftKeyboard())

            // Save transaction
            onView(withId(R.id.btn_save_transaction)).perform(scrollTo(), click())
        }

        // Verify transaction is saved in Room database with receiptImagePath == null
        val savedTransactions = app.transactionRepository.allTransactions.first()
        assertEquals(1, savedTransactions.size)
        val saved = savedTransactions[0]
        assertEquals(42.50, saved.amount, 0.001)
        assertEquals("Local Bakery", saved.merchant)
        assertNull("Transaction without receipt must have null receiptImagePath", saved.receiptImagePath)
    }

    /**
     * Confirms that a transaction saved with a receiptImagePath persists into Room,
     * survives DB reloading, and the file exists on app-private external storage.
     */
    @Test
    fun testTransactionWithReceipt_persistsToDatabaseAndSurvives(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Checking", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        // Create a mock receipt file in external files dir
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val receiptFile = File(storageDir, "receipt_test_${System.currentTimeMillis()}.jpg")
        createMockReceiptImage(receiptFile, 400, 600)
        assertTrue(receiptFile.exists() && receiptFile.length() > 0)

        // Save transaction with receipt file path
        val tx = Transaction(
            accountId = accountId,
            amount = 89.99,
            merchant = "Supermarket",
            receiptImagePath = receiptFile.absolutePath
        )
        val txId = app.transactionRepository.insert(tx).toInt()

        // Re-query from DB to verify persistence
        val reloaded = app.transactionRepository.getById(txId)
        assertNotNull(reloaded)
        assertEquals(receiptFile.absolutePath, reloaded!!.receiptImagePath)
        assertTrue("Persisted receipt file must exist on disk", File(reloaded.receiptImagePath!!).exists())

        // Verify safe downsampling decode via ImageUtil
        val decoded = ImageUtil.decodeSampledBitmapFromFile(reloaded.receiptImagePath!!, 200, 200)
        assertNotNull("ImageUtil must decode valid file", decoded)
        decoded?.recycle()

        // Clean up test file
        receiptFile.delete()
    }

    /**
     * Confirms that TransactionDetailActivity shows the receipt image section when
     * receiptImagePath is present, and completely hides it when receiptImagePath is null.
     */
    @Test
    fun testTransactionDetail_displaysReceiptWhenAttached_andHidesWhenNull(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Bank Account", type = AccountType.BANK.storageValue, balance = 1000.0)
        ).toInt()

        // 1. Transaction WITH receipt
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val receiptFile = File(storageDir, "receipt_detail_test.jpg")
        createMockReceiptImage(receiptFile, 300, 400)

        val txWithReceiptId = app.transactionRepository.insert(
            Transaction(
                accountId = accountId,
                amount = 75.0,
                merchant = "Electronics Store",
                receiptImagePath = receiptFile.absolutePath
            )
        ).toInt()

        val intentWithReceipt = Intent(context, TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txWithReceiptId)
        }
        ActivityScenario.launch<TransactionDetailActivity>(intentWithReceipt).use {
            onView(withId(R.id.layout_receipt_detail_section)).check(matches(isDisplayed()))
            onView(withId(R.id.iv_detail_receipt_image)).check(matches(isDisplayed()))
        }

        // 2. Transaction WITHOUT receipt
        val txWithoutReceiptId = app.transactionRepository.insert(
            Transaction(
                accountId = accountId,
                amount = 15.0,
                merchant = "Quick Bite",
                receiptImagePath = null
            )
        ).toInt()

        val intentWithoutReceipt = Intent(context, TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, txWithoutReceiptId)
        }
        ActivityScenario.launch<TransactionDetailActivity>(intentWithoutReceipt).use {
            onView(withId(R.id.layout_receipt_detail_section)).check(matches(not(isDisplayed())))
        }

        // Clean up test file
        receiptFile.delete()
    }

    /**
     * Confirms that editing a transaction which already has a receipt shows the existing
     * thumbnail and allows removing or replacing it.
     */
    @Test
    fun testEditTransaction_showsExistingReceiptThumbnail_andAllowsRemoval(): Unit = runBlocking {
        val accountId = app.accountRepository.insert(
            Account(name = "Card", type = AccountType.CREDIT_CARD.storageValue, balance = 200.0)
        ).toInt()

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val receiptFile = File(storageDir, "receipt_edit_test.jpg")
        createMockReceiptImage(receiptFile, 300, 300)

        val txId = app.transactionRepository.insert(
            Transaction(
                accountId = accountId,
                amount = 55.0,
                merchant = "Department Store",
                receiptImagePath = receiptFile.absolutePath
            )
        ).toInt()

        val editIntent = Intent(context, AddEditTransactionActivity::class.java).apply {
            putExtra(AddEditTransactionActivity.EXTRA_TRANSACTION_ID, txId)
        }

        ActivityScenario.launch<AddEditTransactionActivity>(editIntent).use {
            // Verify preview is visible with thumbnail and replace/remove buttons
            onView(withId(R.id.layout_receipt_preview)).check(matches(isDisplayed()))
            onView(withId(R.id.iv_receipt_thumbnail)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_replace_receipt)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_remove_receipt)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_scan_receipt)).check(matches(not(isDisplayed())))

            // Tap remove receipt
            onView(withId(R.id.btn_remove_receipt)).perform(click())

            // Verify preview is hidden and scan button reappears
            onView(withId(R.id.layout_receipt_preview)).check(matches(not(isDisplayed())))
            onView(withId(R.id.btn_scan_receipt)).check(matches(isDisplayed()))

            // Save changes
            onView(withId(R.id.btn_save_transaction)).perform(scrollTo(), click())
        }

        // Verify updated transaction in DB now has receiptImagePath == null
        val updatedTx = app.transactionRepository.getById(txId)
        assertNotNull(updatedTx)
        assertNull("Receipt image path should be cleared after removal and save", updatedTx!!.receiptImagePath)

        // Clean up test file
        receiptFile.delete()
    }

    /**
     * Confirms that ImageUtil calculateInSampleSize and decodeSampledBitmapFromFile
     * downscale high-resolution images properly to protect against OutOfMemoryErrors.
     */
    @Test
    fun testImageUtil_downsamplesLargeImageProperly() {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val largeImageFile = File(storageDir, "receipt_large_test.jpg")
        // Create a 2400 x 1800 simulated camera image
        createMockReceiptImage(largeImageFile, 2400, 1800)

        // Decode downscaled to 200x200
        val downsampled = ImageUtil.decodeSampledBitmapFromFile(largeImageFile.absolutePath, 200, 200)
        assertNotNull("Downsampled bitmap must decode successfully", downsampled)
        assertTrue("Width should be downscaled significantly", downsampled!!.width <= 600)
        assertTrue("Height should be downscaled significantly", downsampled.height <= 600)

        downsampled.recycle()
        largeImageFile.delete()
    }

    /**
     * Helper to create a dummy JPEG bitmap file on disk for tests.
     */
    private fun createMockReceiptImage(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        canvas.drawText("Test Receipt", 20f, 50f, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }
}

package com.phelat.poolakey

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.phelat.poolakey.callback.ConnectionCallback
import com.phelat.poolakey.callback.ConsumeCallback
import com.phelat.poolakey.callback.PurchaseCallback
import com.phelat.poolakey.callback.PurchaseIntentCallback
import com.phelat.poolakey.callback.PurchaseQueryCallback
import com.phelat.poolakey.config.PaymentConfiguration
import com.phelat.poolakey.mapper.RawDataToPurchaseInfo
import com.phelat.poolakey.request.PurchaseRequest
import com.phelat.poolakey.thread.BackgroundThread
import com.phelat.poolakey.thread.MainThread
import com.phelat.poolakey.thread.PoolakeyThread

class Payment(context: Context, config: PaymentConfiguration = PaymentConfiguration()) {

    private val rawDataToPurchaseInfo = RawDataToPurchaseInfo()

    private val backgroundThread = BackgroundThread()

    private val mainThread: PoolakeyThread<() -> Unit> = MainThread()

    private val connection = BillingConnection(
        context = context,
        paymentConfiguration = config,
        rawDataToPurchaseInfo = rawDataToPurchaseInfo,
        backgroundThread = backgroundThread,
        mainThread = mainThread
    )

    private val purchaseResultParser = PurchaseResultParser(rawDataToPurchaseInfo)

    /**
     * You can use this function to connect to the In-App Billing service. Note that you have to
     * connect to Bazaar's Billing service before using any other available functions, So make sure
     * you call this function before doing anything else, also make sure that you are connected to
     * the billing service through com.phelat.poolakey.Connection.
     * @see Connection
     * @param callback That's how you can get notified about service connection changes.
     * @return a com.phelat.poolakey.Connection interface which you can use to disconnect from the
     * service or get the current connection state.
     */
    fun connect(callback: ConnectionCallback.() -> Unit = {}): Connection {
        return connection.startConnection(callback)
    }

    /**
     * You can use this function to navigate user to Bazaar's payment activity to purchase an item.
     * Note that for subscribing an item you have to use the 'subscribeItem' function.
     * @see subscribeItem
     * @param activity We use this activity instance to actually start Bazaar's payment activity.
     * @param request This contains some information about the product that we are going to purchase.
     * @param callback That's how you can get notified about the purchase flow. Note that this
     * callback is only used for notifying about the purchase flow and if you want to get notified
     * if user actually purchased the item, you have to use the 'onActivityResult' function.
     * @see onActivityResult
     */
    fun purchaseItem(
        activity: Activity,
        request: PurchaseRequest,
        callback: PurchaseIntentCallback.() -> Unit
    ) {
        requestCode = request.requestCode
        connection.purchase(activity, request, PurchaseType.IN_APP, callback)
    }

    /**
     * You can use this function to navigate user to Bazaar's payment activity to purchase an item.
     * Note that for subscribing an item you have to use the 'subscribeItem' function.
     * @see subscribeItem
     * @param fragment We use this fragment instance to actually start Bazaar's payment activity.
     * @param request This contains some information about the product that we are going to purchase.
     * @param callback That's how you can get notified about the purchase flow. Note that this
     * callback is only used for notifying about the purchase flow and if you want to get notified
     * if user actually purchased the item, you have to use the 'onActivityResult' function.
     * @see onActivityResult
     */
    fun purchaseItem(
        fragment: Fragment,
        request: PurchaseRequest,
        callback: PurchaseIntentCallback.() -> Unit
    ) {
        requestCode = request.requestCode
        connection.purchase(fragment, request, PurchaseType.IN_APP, callback)
    }

    /**
     * You can use this function to consume an already purchased item. Note that you can't use this
     * function to consume subscribed items. This function runs off the main thread, so you don't
     * have to handle the threading by your self.
     * @param purchaseToken You have received this token when user purchased that particular item.
     * You can also use 'getPurchasedItems' function to get all the purchased items by this
     * particular user.
     * @param callback That's how you can get notified if product consumption was successful or not
     * @see getPurchasedItems
     */
    fun consumeItem(purchaseToken: String, callback: ConsumeCallback.() -> Unit) {
        connection.consume(purchaseToken, callback)
    }

    fun subscribeItem(
        activity: Activity,
        request: PurchaseRequest,
        callback: PurchaseIntentCallback.() -> Unit
    ) {
        requestCode = request.requestCode
        connection.purchase(activity, request, PurchaseType.SUBSCRIPTION, callback)
    }

    fun subscribeItem(
        fragment: Fragment,
        request: PurchaseRequest,
        callback: PurchaseIntentCallback.() -> Unit
    ) {
        requestCode = request.requestCode
        connection.purchase(fragment, request, PurchaseType.SUBSCRIPTION, callback)
    }

    fun getPurchasedItems(callback: PurchaseQueryCallback.() -> Unit) {
        connection.queryBoughtItems(PurchaseType.IN_APP, callback)
    }

    fun getSubscribedItems(callback: PurchaseQueryCallback.() -> Unit) {
        connection.queryBoughtItems(PurchaseType.SUBSCRIPTION, callback)
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        purchaseCallback: PurchaseCallback.() -> Unit
    ) {
        if (Payment.requestCode > -1 && Payment.requestCode == requestCode) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    purchaseResultParser.handleReceivedResult(data, purchaseCallback)
                }
                Activity.RESULT_CANCELED -> {
                    PurchaseCallback().apply(purchaseCallback)
                        .purchaseCanceled
                        .invoke()
                }
                else -> {
                    PurchaseCallback().apply(purchaseCallback)
                        .purchaseFailed
                        .invoke(IllegalStateException("Result code is not valid"))
                }
            }
        }
    }

    companion object {
        @Volatile
        private var requestCode: Int = -1
    }

}

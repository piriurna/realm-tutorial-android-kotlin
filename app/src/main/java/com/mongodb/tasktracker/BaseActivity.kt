package com.mongodb.tasktracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var realm: Realm
    protected var user: User? = null
    protected fun isUserLoggedIn() : Boolean = user != null
    protected lateinit var partition: String


    abstract fun getLayoutId() : Int


    override fun onStart() {
        super.onStart()


        if(intent.extras != null){
            // get the partition value and name of the project we are currently viewing
            partition = intent?.extras?.getString(PARTITION_EXTRA_KEY)!!
            val projectName = intent?.extras?.getString(PROJECT_NAME_EXTRA_KEY)

            if(projectName != null){
                // display the name of the project in the action bar via the title member variable of the Activity
                title = projectName
            }
        }

        user = taskApp.currentUser()
        if (user == null) {
            // if no user is currently logged in, start the login activity so the user can authenticate
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {
            val config = (if (intent.extras != null) SyncConfiguration.Builder(user!!, partition) else SyncConfiguration.Builder(user!!, "user=${user!!.id}")).build()


            // Sync all realm changes via a new instance, and when that instance has been successfully created connect it to an on-screen list (a recycler view)
            Realm.getInstanceAsync(config, object: Realm.Callback() {
                override fun onSuccess(realm: Realm) {
                    // since this realm should live exactly as long as this activity, assign the realm to a member variable
                    onRealmReady(realm)
                }
            })
           authorizedOnStart()
        }
    }

    open fun authorizedOnStart(){

    }

    open fun onRealmReady(realm: Realm){

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())
        authorizedOnCreate(savedInstanceState)
    }


    open fun authorizedOnCreate(savedInstanceState: Bundle?){

    }

    override fun onStop() {
        super.onStop()
        user.run {
            realm.close()
        }
    }

    override fun onDestroy(){
        super.onDestroy()
        realm.close()
    }
}
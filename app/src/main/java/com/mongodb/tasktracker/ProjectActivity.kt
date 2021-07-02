package com.mongodb.tasktracker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mongodb.tasktracker.model.Project
import com.mongodb.tasktracker.model.ProjectAdapter
import com.mongodb.tasktracker.model.User
import io.realm.*
import io.realm.kotlin.where
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.types.ObjectId

/*
* ProjectActivity: allows a user to view a collection of Projects. Clicking on a project launches a
* view of tasks in that project. Clicking on the options button for a project launches a view
* that allows the user to add or remove members from the project. All projects are stored in a
* read-only realm on the logged in user's User object.
*/
class ProjectActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProjectAdapter

    override fun getLayoutId(): Int = R.layout.activity_project

    /**
     * We need to define the realm for this class. Trying to find a more elegant solution...
     */
    override fun onRealmReady(realm: Realm) {
        this@ProjectActivity.realm = realm
        setUpRecyclerView(getProjects(realm))
    }

    override fun authorizedOnCreate(savedInstanceState: Bundle?) {
        recyclerView = findViewById(R.id.project_list)
    }


    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_task_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                user?.logOutAsync {
                    if (it.isSuccess) {
                        user = null
                        Log.v(TAG(), "user logged out")
                        startActivity(Intent(this, LoginActivity::class.java))
                    } else {
                        Log.e(TAG(), "log out failed! Error: ${it.error}")
                    }
                }
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun getProjects(realm: Realm): RealmList<Project> {
        // query for a user object in our user realm, which should only contain our user object
        val syncedUsers : RealmResults<User> = realm.where<User>().sort("id").findAll()
        val syncedUser : User? = syncedUsers.getOrNull(0) // since there might be no user objects in the results, default to "null"
        // if a user object exists, create the recycler view and the corresponding adapter
        if (syncedUser != null) {
            return syncedUser.memberOf
        } else {
            // since a trigger creates our user object after initial signup, the object might not exist immediately upon first login.
            // if the user object doesn't yet exist (that is, if there are no users in the user realm), call this function again when it is created
            Log.i(TAG(), "User object not yet initialized, only showing default user project until initialization.")
            // change listener on a query for our user object lets us know when the user object has been created by the auth trigger
            val changeListener =
                OrderedRealmCollectionChangeListener<RealmResults<User>> { results, changeSet ->
                    Log.i(TAG(), "User object initialized, displaying project list.")
                    setUpRecyclerView(getProjects(realm))
                }
            syncedUsers.addChangeListener(changeListener)


            // user should have a personal project no matter what, so create it if it doesn't already exist
            // RealmRecyclerAdapters only work on managed objects,
            // so create a realm to manage a fake custom user data object
            // offline, in-memory because this data does not need to be persistent or synced:
            // the object is only used to determine the partition for storing tasks
            val fakeRealm = Realm.getInstance(
                RealmConfiguration.Builder()
                    .allowWritesOnUiThread(true)
                    .inMemory().build())
            var projectsList: RealmList<Project>? = null
            var fakeCustomUserData = fakeRealm.where(User::class.java).findFirst()
            if (fakeCustomUserData == null) {
                fakeRealm.executeTransaction {
                    fakeCustomUserData = it.createObject(User::class.java, user?.id)
                    projectsList = fakeCustomUserData?.memberOf!!
                    projectsList?.add(Project("My Project", "project=${user?.id}"))
                }
            } else {
                projectsList = fakeCustomUserData?.memberOf
            }

            return projectsList!!
        }
    }

    private fun setUpRecyclerView(projectsList: RealmList<Project>) {
        adapter = ProjectAdapter(projectsList, user!!)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
    }
}

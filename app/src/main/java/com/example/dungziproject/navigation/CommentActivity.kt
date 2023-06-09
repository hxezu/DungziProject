package com.example.dungziproject.navigation

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.dungziproject.R
import com.example.dungziproject.databinding.ActivityCommentBinding
import com.example.dungziproject.databinding.AlbumCommentItemBinding
import com.example.dungziproject.navigation.model.ContentDTO
import com.example.dungziproject.navigation.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class CommentActivity : AppCompatActivity() {
    lateinit var binding: ActivityCommentBinding
    var contentUid : String?= null
    var contentDTO: ContentDTO? = null
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initLayout()
    }

    private fun initLayout(){
        auth = Firebase.auth
        contentUid = intent.getStringExtra("contentUid")

        // Load content
        FirebaseFirestore.getInstance()
            .collection("images")
            .document(contentUid!!)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    contentDTO = task.result?.toObject(ContentDTO::class.java)
                    if (contentDTO != null) {
                        Glide.with(this).load(contentDTO!!.imgUrl)
                            .apply(RequestOptions().fitCenter())
                            .into(binding.contentImg)
                        binding.contentText.text = contentDTO!!.explain
                        binding.likeTextview.text = contentDTO!!.favoriteCount.toString()
                        binding.commentCountTextview.text = contentDTO!!.commentCount.toString()
                    }
                }
                if(contentDTO == null){
                    contentDTO = ContentDTO()
                }

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(contentUid!!)
                    .get()
                    .addOnSuccessListener { userSnapshot ->
                        binding.writerIdTV.text = contentDTO!!.nickname
                        val userRef =
                            FirebaseDatabase.getInstance().getReference("user").child(contentDTO?.userId ?: "")
                        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                val user = dataSnapshot.getValue(User::class.java)
                                var resId = resources.getIdentifier("@raw/" + user?.image, "raw", packageName)
                                binding.profileImageview.setImageResource(resId)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                // 처리할 작업을 추가하세요
                            }

                        })
                        val timestamp = contentDTO?.timestamp
                        val uploadTime = SimpleDateFormat("yy.MM.dd HH:mm", Locale.getDefault()).format(
                            timestamp?.let { Date(it) })
                        binding.ImgUploadTimeTV.text = uploadTime

                        if (auth.currentUser?.uid == contentDTO?.userId) {
                            // 현재 사용자와 게시물 업로드 사용자가 같은 경우에만 팝업 메뉴 보여주기
                            binding.editPopup.visibility = View.VISIBLE
                            binding.editPopup.setOnClickListener {
                                showPopup(it)
                            }

                        } else {
                            // 다른 경우 팝업 메뉴 비활성화
                            binding.editPopup.visibility = View.GONE
                        }
                    }

                FirebaseFirestore.getInstance()
                    .collection("images")
                    .document(contentUid!!)
                    .collection("comments")
                    .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                        if (querySnapshot != null) {
                            val count = querySnapshot.size() // Get the count of comments
                            binding.commentCountTextview.text = count.toString() // Update the comment count TextView
                        }
                    }

            }

        binding.backBtn.setOnClickListener {
            finish()
        }

        //Comment
        binding.commentRecyclerview.adapter = CommentRecyclerViewAdapter()
        binding.commentRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.sendBtn.setOnClickListener {
            var comment = ContentDTO.Comment()
            comment.userId = FirebaseAuth.getInstance().currentUser?.uid
            comment.comment = binding.commentEdittext.text.toString()
            comment.timestamp = System.currentTimeMillis()

            FirebaseFirestore.getInstance().collection("images").document(contentUid!!).collection("comments").document().set(comment)
            binding.commentEdittext.setText("")
        }
    }

    private fun showPopup(view : View) {
        val popupMenu = PopupMenu(this@CommentActivity, view)
        popupMenu.inflate(R.menu.content_popup)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    editContent()
                }
                R.id.action_delete -> {
                    deleteContent()
                }
            }
            false
        }
        popupMenu.show()
    }

    fun editContent(){
        val intent = Intent(this@CommentActivity, EditContentActivity::class.java)
        intent.putExtra("contentUid", contentUid)
        startActivity(intent)
        finish()
    }

    fun deleteContent(){
        FirebaseFirestore.getInstance()
            .collection("images")
            .document(contentUid!!)
            .delete()
            .addOnSuccessListener {
                // 삭제 성공
                finish() // 액티비티 종료 또는 필요한 작업 수행
            }
    }


    //Comment RecyclerView
    inner class CommentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        var comments : ArrayList<ContentDTO.Comment> = arrayListOf()
        init {
            FirebaseFirestore.getInstance()
                .collection("images")
                .document(contentUid!!)
                .collection("comments")
                .orderBy("timestamp")
                .addSnapshotListener { querySnapshot, firebaseFilestoreException ->
                    comments.clear()
                    if(querySnapshot == null) return@addSnapshotListener

                    for(snapshot in querySnapshot.documents!!){
                        val comment = snapshot.toObject(ContentDTO.Comment::class.java)
                        comment?.commentId = snapshot.id // Assign commentId
                        comment?.let { comments.add(it) }
                    }
                    updateCommentCount(comments.size)
                    notifyDataSetChanged()
                }
        }

        private fun updateCommentCount(count: Int) {
            contentDTO?.commentCount = count
            contentDTO?.let {
                FirebaseFirestore.getInstance()
                    .collection("images")
                    .document(contentUid!!)
                    .set(it)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val itemBinding = AlbumCommentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CustomViewHolder(itemBinding)
        }

        private inner class CustomViewHolder(val itemBinding: AlbumCommentItemBinding) : RecyclerView.ViewHolder(itemBinding.root)


        override fun getItemCount(): Int {
            return comments.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val customViewHolder = holder as CustomViewHolder
            val comment = comments[position]

            customViewHolder.itemBinding.messageTextview.text = comment.comment
            holder.itemBinding.commentTimeTextview.text = SimpleDateFormat("MM월 dd일 HH:mm",
                Locale.getDefault()).format(Date(comment.timestamp!!))
            
            val userRef =
                FirebaseDatabase.getInstance().getReference("user").child(comment.userId ?: "")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val user = dataSnapshot.getValue(User::class.java)
                    customViewHolder.itemBinding.profileTextview.text = user?.nickname // 수정된 부분
                    var resId = resources.getIdentifier("@raw/" + user?.image, "raw", packageName)
                    customViewHolder.itemBinding.profileImageview.setImageResource(resId)
                }
                override fun onCancelled(error: DatabaseError) {
                    // 처리할 작업을 추가하세요
                }
            })

            if(comment.userId == FirebaseAuth.getInstance().currentUser?.uid){
                holder.itemBinding.root.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Get the position of the comment item in the RecyclerView
                        val itemPosition = holder.adapterPosition
                        // Start the swipe-to-delete action
                        startSwipeToDelete(itemPosition)
                    }
                    false
                }

            }
        }

        private fun startSwipeToDelete(position: Int){
            // Create a callback for swipe-to-delete action
            val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    // Not needed for swipe-to-delete
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    if (direction == ItemTouchHelper.LEFT) {
                        // Swipe-to-delete action is performed
                        val commentToDelete = comments[viewHolder.adapterPosition]
                                deleteComment(commentToDelete)
                    }
                }
            }

            // Create an ItemTouchHelper with the swipe-to-delete callback
            val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            // Attach the ItemTouchHelper to the RecyclerView
            itemTouchHelper.attachToRecyclerView(binding.commentRecyclerview)
        }

        private fun deleteComment(comment: ContentDTO.Comment) {
            FirebaseFirestore.getInstance()
                .collection("images")
                .document(contentUid!!)
                .collection("comments")
                .document(comment.commentId!!)
                .delete()
                .addOnSuccessListener {
                    // Comment deletion is successful
                    // You can show a toast message or perform any necessary action
                }
                .addOnFailureListener { e ->
                    // Handle comment deletion failure
                    Log.e(TAG, "Failed to delete comment: ${e.message}")
                }
        }
    }

}
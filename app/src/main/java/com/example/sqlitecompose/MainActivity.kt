package com.example.sqlitecompose

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // =========================
    // CORES DO TEMA
    // =========================

    private val StudyBlue = Color(0xFF5B7BFE)
    private val StudyPurple = Color(0xFF8E97FD)
    private val BackgroundTop = Color(0xFFF4F7FF)
    private val BackgroundBottom = Color(0xFFE9EEFF)
    private val CardColor = Color.White
    private val TextDark = Color(0xFF1F2937)
    private val SoftGray = Color(0xFF6B7280)
    private val Danger = Color(0xFFE53935)

    // =========================
    // MODEL
    // =========================

    data class Note(
        val id: Long? = null,
        val title: String,
        val content: String,
        val date: Long,
        val tags: List<String>
    )

    // =========================
    // SQLITE
    // =========================

    class DBHelper(context: Context) :
        SQLiteOpenHelper(context, "app.db", null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE notes(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    tags TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun fromDate(date: Long): Long {
            return date
        }

        private fun fromList(list: List<String>): String {
            return JSONArray(list).toString()
        }

        private fun toList(json: String): List<String> {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                array.getString(index)
            }
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            db.execSQL("DROP TABLE IF EXISTS notes")
            onCreate(db)
        }

        fun insertNote(note: Note): Long {

            val cv = ContentValues().apply {
                put("title", note.title)
                put("content", note.content)
                put("date", fromDate(note.date))
                put("tags", fromList(note.tags))
            }

            return writableDatabase.insert(
                "notes",
                null,
                cv
            )
        }

        fun updateNote(note: Note): Int {

            requireNotNull(note.id) {
                "ID não pode ser nulo para update"
            }

            val cv = ContentValues().apply {
                put("title", note.title)
                put("content", note.content)
                put("date", fromDate(note.date))
                put("tags", fromList(note.tags))
            }

            return writableDatabase.update(
                "notes",
                cv,
                "id=?",
                arrayOf(note.id.toString())
            )
        }

        fun deleteNote(id: Long): Int {

            return writableDatabase.delete(
                "notes",
                "id=?",
                arrayOf(id.toString())
            )
        }

        fun getAllNotes(): List<Note> {

            val list = mutableListOf<Note>()

            val c: Cursor = readableDatabase.rawQuery(
                "SELECT id, title, content, date, tags FROM notes ORDER BY id DESC",
                null
            )

            c.use { cur ->

                val idIdx = cur.getColumnIndexOrThrow("id")
                val titleIdx = cur.getColumnIndexOrThrow("title")
                val contentIdx = cur.getColumnIndexOrThrow("content")
                val dateIdx = cur.getColumnIndexOrThrow("date")
                val tagsIdx = cur.getColumnIndexOrThrow("tags")

                while (cur.moveToNext()) {

                    list.add(
                        Note(
                            id = cur.getLong(idIdx),
                            title = cur.getString(titleIdx),
                            content = cur.getString(contentIdx),
                            date = cur.getLong(dateIdx),
                            tags = toList(cur.getString(tagsIdx))
                        )
                    )
                }
            }

            return list
        }
    }

    // =========================
    // ON CREATE
    // =========================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = DBHelper(this)

        setContent {

            MaterialTheme {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    BackgroundTop,
                                    BackgroundBottom
                                )
                            )
                        )
                ) {
                    NotesScreen(dbHelper = db)
                }
            }
        }
    }

    // =========================
    // TELA PRINCIPAL
    // =========================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NotesScreen(dbHelper: DBHelper) {

        var notes by remember {
            mutableStateOf(dbHelper.getAllNotes())
        }

        var title by remember {
            mutableStateOf(TextFieldValue(""))
        }

        var content by remember {
            mutableStateOf(TextFieldValue(""))
        }

        var dateText by remember {
            mutableStateOf(TextFieldValue(""))
        }

        var tagsText by remember {
            mutableStateOf(TextFieldValue(""))
        }

        var editingId by remember {
            mutableStateOf<Long?>(null)
        }

        fun clearFields() {

            title = TextFieldValue("")
            content = TextFieldValue("")
            dateText = TextFieldValue("")
            tagsText = TextFieldValue("")
            editingId = null
        }

        Scaffold(
            containerColor = Color.Transparent,

            topBar = {

                TopAppBar(

                    title = {

                        Column {

                            Text(
                                text = "Study Planner",
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            Text(
                                text =
                                    if (editingId == null)
                                        "Organize seus estudos"
                                    else
                                        "Editando anotação",

                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray
                            )
                        }
                    },

                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {

                // =========================
                // FORMULÁRIO
                // =========================

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardColor
                    ),

                    shape = RoundedCornerShape(24.dp),

                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {

                        Text(
                            text = "Nova sessão de estudo",

                            style = MaterialTheme.typography.titleLarge,

                            fontWeight = FontWeight.Bold,

                            color = TextDark
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },

                            label = {
                                Text("Matéria")
                            },

                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },

                            label = {
                                Text("Conteúdo")
                            },

                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },

                            label = {
                                Text("Data (dd/mm/aaaa)")
                            },

                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = tagsText,
                            onValueChange = { tagsText = it },

                            label = {
                                Text("Tarefas")
                            },

                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Button(

                                onClick = {

                                    val formatter = SimpleDateFormat(
                                        "dd/MM/yyyy",
                                        Locale.getDefault()
                                    )

                                    val date = try {

                                        formatter.parse(
                                            dateText.text
                                        )?.time
                                            ?: System.currentTimeMillis()

                                    } catch (e: Exception) {

                                        System.currentTimeMillis()
                                    }

                                    val tags = tagsText.text
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }

                                    val t = title.text.trim()
                                    val c = content.text.trim()

                                    if (t.isEmpty() || c.isEmpty()) {
                                        return@Button
                                    }

                                    if (editingId == null) {

                                        dbHelper.insertNote(

                                            Note(
                                                title = t,
                                                content = c,
                                                date = date,
                                                tags = tags
                                            )
                                        )

                                    } else {

                                        dbHelper.updateNote(

                                            Note(
                                                id = editingId,
                                                title = t,
                                                content = c,
                                                date = date,
                                                tags = tags
                                            )
                                        )
                                    }

                                    notes = dbHelper.getAllNotes()

                                    clearFields()
                                },

                                shape = RoundedCornerShape(16.dp),

                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudyBlue
                                )
                            ) {

                                Text(
                                    if (editingId == null)
                                        "Salvar"
                                    else
                                        "Atualizar"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFields()
                                },

                                shape = RoundedCornerShape(16.dp)
                            ) {

                                Text("Limpar")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider()

                Spacer(Modifier.height(12.dp))

                // =========================
                // LISTA
                // =========================

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 80.dp
                    )
                ) {

                    items(
                        notes,
                        key = { it.id ?: -1 }
                    ) { note ->

                        NoteItem(
                            note = note,

                            onClick = {

                                editingId = note.id

                                title = TextFieldValue(note.title)

                                content = TextFieldValue(note.content)

                                dateText = TextFieldValue(
                                    SimpleDateFormat(
                                        "dd/MM/yyyy",
                                        Locale.getDefault()
                                    ).format(Date(note.date))
                                )

                                tagsText = TextFieldValue(
                                    note.tags.joinToString(", ")
                                )
                            },

                            onDelete = { id ->

                                dbHelper.deleteNote(id)

                                notes = dbHelper.getAllNotes()

                                if (editingId == id) {
                                    clearFields()
                                }
                            }
                        )

                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    // =========================
    // ITEM DA LISTA
    // =========================

    @Composable
    private fun NoteItem(
        note: Note,
        onClick: () -> Unit,
        onDelete: (Long) -> Unit
    ) {

        val formattedDate = SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.getDefault()
        ).format(Date(note.date))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },

            shape = RoundedCornerShape(22.dp),

            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            ),

            colors = CardDefaults.cardColors(
                containerColor = CardColor
            )
        ) {

            Column(
                modifier = Modifier.padding(18.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column {

                        Text(
                            text = note.title,

                            style = MaterialTheme.typography.titleLarge,

                            fontWeight = FontWeight.Bold,

                            color = TextDark
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "📅 $formattedDate",
                            color = SoftGray
                        )
                    }

                    if (note.id != null) {

                        TextButton(
                            onClick = {
                                onDelete(note.id)
                            }
                        ) {

                            Text(
                                "Excluir",
                                color = Danger
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = note.content,

                    style = MaterialTheme.typography.bodyLarge,

                    color = TextDark
                )

                if (note.tags.isNotEmpty()) {

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Tarefas",

                        fontWeight = FontWeight.Bold,

                        color = StudyBlue
                    )

                    Spacer(Modifier.height(6.dp))

                    note.tags.forEach { tag ->

                        Text(
                            text = "• $tag",
                            color = SoftGray
                        )
                    }
                }
            }
        }
    }
}
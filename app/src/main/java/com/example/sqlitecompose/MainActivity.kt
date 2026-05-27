package com.example.sqlitecompose

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // --- Data model simples ---
    data class Note(
        val id: Long? = null,
        val title: String,
        val content: String,
        val date: Long,
        val tags: List<String>
    )

    // --- Helper SQLite (dentro da MainActivity para manter "uma única activity") ---
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

        // Date <-> Long
        private fun fromDate(date: Long): Long {
            return date
        }

        // List<String> <-> JSON String
        private fun fromList(list: List<String>): String {
            return JSONArray(list).toString()
        }

        private fun toList(json: String): List<String> {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                array.getString(index)
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
            return writableDatabase.insert("notes", null, cv)
        }

        fun updateNote(note: Note): Int {
            requireNotNull(note.id) { "ID não pode ser nulo para update" }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DBHelper(this)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    NotesScreen(dbHelper = db)
                }
            }
        }
    }

    // --- UI Compose + lógica CRUD ---
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NotesScreen(dbHelper: DBHelper) {
        var notes by remember { mutableStateOf(dbHelper.getAllNotes()) }

        // Estados para criação/edição
        var title by remember { mutableStateOf(TextFieldValue("")) }
        var content by remember { mutableStateOf(TextFieldValue("")) }
        var dateText by remember { mutableStateOf(TextFieldValue("")) }
        var tagsText by remember { mutableStateOf(TextFieldValue("")) }

        // Estado para saber se estamos editando algo
        var editingId by remember { mutableStateOf<Long?>(null) }

        fun clearFields() {
            title = TextFieldValue("")
            content = TextFieldValue("")
            dateText = TextFieldValue("")
            tagsText = TextFieldValue("")
            editingId = null
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (editingId == null) "App de lista de estudos" else "Editando #$editingId")
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Matéria") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Conteúdo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data (dd/mm/aaaa)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tarefas (separadas por vírgula)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row {
                    Button(
                        onClick = {
                            val formatter = SimpleDateFormat(
                                "dd/MM/yyyy",
                                Locale.getDefault()
                            )

                            val date = try {
                                formatter.parse(dateText.text)?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }

                            val tags = tagsText.text
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            val t = title.text.trim()
                            val c = content.text.trim()
                            if (t.isEmpty() || c.isEmpty()) return@Button

                            if (editingId == null) {
                                // CREATE
                                dbHelper.insertNote(
                                    Note(
                                        title = t,
                                        content = c,
                                        date = date,
                                        tags = tags
                                    )
                                )
                            } else {
                                // UPDATE
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

                        }
                    ) {
                        Text(if (editingId == null) "Salvar" else "Atualizar")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { clearFields() }) {
                        Text("Limpar")
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(notes, key = { it.id ?: -1 }) { note ->
                        NoteItem(
                            note = note,
                            onClick = {
                                // carregar no form para edição
                                editingId = note.id
                                title = TextFieldValue(note.title)
                                content = TextFieldValue(note.content)
                                dateText = TextFieldValue(
                                    SimpleDateFormat(
                                        "dd/MM/yyyy",
                                        Locale.getDefault()
                                    ).format(Date(note.date))
                                )
                                tagsText = TextFieldValue(note.tags.joinToString(", "))
                            },
                            onDelete = { id ->
                                dbHelper.deleteNote(id)
                                notes = dbHelper.getAllNotes()
                                if (editingId == id) clearFields()
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }

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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { onClick() }
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = note.title, style = MaterialTheme.typography.titleMedium)
                if (note.id != null) {
                    TextButton(onClick = { onDelete(note.id) }) {
                        Text("Excluir")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = note.content, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(4.dp))
            Text("Data: $formattedDate")

            Spacer(Modifier.height(4.dp))
            Text("Tarefas: ${note.tags.joinToString()}")
        }
    }
}

package de.utrost.textr

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RoadmapCompletionDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val device get()=UiDevice.getInstance(instrumentation)
    private val sessions=linkedSetOf<EditorSession>()
    private fun fixture(name:String,text:String):Uri=Uri.parse("content://de.utrost.textr.test.documents/$name").also {
        context.contentResolver.call(it,"replace",it.toString(),Bundle().apply{putString("text",text)})
    }
    private fun outputFixture(name:String)=context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"),"output-fixture",name,Bundle().apply{putString("operation","configure");putString("mode","normal")})
    private fun target(name:String)=DocumentsContract.buildDocumentUri("de.utrost.textr.test.outputs",name)
    @Before @After fun reset() {
        for(session in sessions) {
            val worker=EditorSession::class.java.getDeclaredField("worker").apply{isAccessible=true}.get(session) as java.util.concurrent.ExecutorService
            assertTrue(worker.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS))
        }
        sessions.clear();File(context.noBackupFilesDir,"edits").deleteRecursively()
        ReadingCollections(context).clear()
        for(name in listOf("output_operation","reading","open_documents")) context.getSharedPreferences(name,0).edit().clear().commit()
        DocumentRepository(context).setSessionReferences(emptySet());DocumentRepository(context).clear()
    }
    private fun editor(text:String):ActivityScenario<EditorActivity> {
        val uri=fixture("roadmap-editor.md",text)
        val token=EditorInbox.put(EditorSnapshot(TextDocument("roadmap-editor.md",text,FileKind.MARKDOWN,text.toByteArray().size),uri.toString(),false))!!
        return ActivityScenario.launch(Intent(context,EditorActivity::class.java).putExtra("token",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun editorModel(activity:EditorActivity)=ViewModelProvider(activity)[EditorSession::class.java].also{sessions.add(it)}
    private fun editorReady(scenario:ActivityScenario<EditorActivity>)=await{var ready=false;scenario.onActivity{ready=editorModel(it).buffer!=null&&!editorModel(it).loading};ready}
    private fun comparisonModel(activity:ComparisonActivity)=ViewModelProvider(activity)[ComparisonSession::class.java]
    private fun comparisonReady(scenario:ActivityScenario<ComparisonActivity>)=await{var ready=false;scenario.onActivity{ready=!comparisonModel(it).busy&&comparisonModel(it).result!=null};ready}
    private fun capture(name:String,result:AtomicReference<Intent>)=object:Instrumentation.ActivityMonitor(){
        override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
            if(intent.component?.className!=name)return null
            result.set(intent);return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
        }
    }.also{instrumentation.addMonitor(it)}
    private fun click(label:Int) { val item=device.wait(Until.findObject(By.text(context.getString(label))),5000);assertNotNull("Missing action $label",item);item!!.click();device.waitForIdle() }
    @Test fun editorLineActionsKeyboardBoundsAndRecreationKeepOneStepUndo() {
        val original="a\r\nb\r\nc\r\n"
        device.executeShellCommand("settings put system font_scale 2.0")
        try{editor(original).use{scenario->
            editorReady(scenario)
            scenario.onActivity{
                it.findViewById<EditText>(R.id.editor_text).setSelection(0,6)
                it.findViewById<Button>(R.id.editor_indent).performClick()
                assertEquals("  a\r\n  b\r\nc\r\n",editorModel(it).buffer!!.text)
                it.findViewById<Button>(R.id.editor_undo).performClick();assertEquals(original,editorModel(it).buffer!!.text)
                assertFalse(editorModel(it).buffer!!.canUndo)
                it.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_G,0,KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON))
            }
            instrumentation.runOnMainSync{
                val root=WindowInspector.getGlobalWindowViews().first{it.findViewById<EditText>(R.id.editor_line_input)!=null}
                val done=root.findViewById<Button>(android.R.id.button1);val rect=android.graphics.Rect();assertTrue(done.getGlobalVisibleRect(rect));assertEquals(done.height,rect.height())
                root.findViewById<EditText>(R.id.editor_line_input).setText("99")
                root.findViewById<Button>(android.R.id.button1).performClick()
                assertNotNull(root.findViewById<EditText>(R.id.editor_line_input).error)
                root.findViewById<EditText>(R.id.editor_line_input).setText("3")
                root.findViewById<Button>(android.R.id.button1).performClick()
            }
            scenario.recreate();editorReady(scenario)
            scenario.onActivity{assertEquals(6,it.findViewById<EditText>(R.id.editor_text).selectionStart);assertEquals(original,editorModel(it).buffer!!.text)}
        }}finally{device.executeShellCommand("settings put system font_scale 1.0")}
    }
    @Test fun draftComparisonCapturesOriginalDespiteProviderChangesAndPreservesUndo() {
        val original="# Original\r\nbody"
        val captured=AtomicReference<Intent>();val monitor=capture(ComparisonActivity::class.java.name,captured)
        try{editor(original).use{scenario->
            editorReady(scenario)
            scenario.onActivity{it.findViewById<EditText>(R.id.editor_text).text.append(" edited")}
            fixture("roadmap-editor.md","provider changed independently")
            scenario.onActivity{it.findViewById<Button>(R.id.editor_compare).performClick()}
            await{captured.get()!=null}
            assertFalse(captured.get().extras.toString().contains(original))
            val pair=DraftComparisonInbox.take(captured.get().getStringExtra("draftToken"))!!
            assertEquals(original,pair.first.document.text);assertEquals(original+" edited",pair.second.document.text)
            assertTrue(pair.sourceUri!!.endsWith("roadmap-editor.md"))
            scenario.recreate();editorReady(scenario)
            scenario.onActivity{assertEquals(original,editorModel(it).draft!!.originalText);it.findViewById<Button>(R.id.editor_undo).performClick();assertEquals(original,editorModel(it).buffer!!.text)}
        }}finally{instrumentation.removeMonitor(monitor)}
        val recovered=EditDraftStore(context).read()!!;assertEquals(original,recovered.originalText)
    }
    @Test fun originalEncodingSurvivesRecoveryAndDraftFormatChanges() {
        val draft=EditDraft.new(TextDocument("utf16.md","original\r\n",FileKind.MARKDOWN,20,encoding="UTF-16LE"),null,bom=true)
        val store=EditDraftStore(context);assertTrue(store.create(draft))
        assertTrue(store.checkpoint(draft.copy(text="edited",encoding=TextEncoding.UTF8,bom=false,baselineEncoding=TextEncoding.UTF8,baselineBom=false,revision=1)))
        val restored=store.read()!!
        assertEquals(TextEncoding.UTF16LE,restored.originalEncoding);assertTrue(restored.originalBom)
        val token=DraftComparisonInbox.put(restored.name,restored.originalText!!,restored.text,null,restored.originalEncoding.charsetName,restored.originalBom,restored.encoding.charsetName,restored.bom)!!
        val pair=DraftComparisonInbox.take(token)!!
        assertEquals("UTF-16LE",pair.first.document.encoding);assertTrue(pair.first.document.hasBom)
        assertEquals("UTF-8",pair.second.document.encoding);assertFalse(pair.second.document.hasBom)
    }
    @Test fun legacyRecoveryDoesNotPretendToKnowOriginalSnapshot() {
        assertTrue(EditDraftStore(context).create(EditDraft.new(TextDocument("old.md","old",FileKind.MARKDOWN,3),null)))
        val file=File(context.noBackupFilesDir,"edits/current.json")
        val json=org.json.JSONObject(file.readText());json.remove("originalText");file.writeText(json.toString())
        ActivityScenario.launch<EditorActivity>(Intent(context,EditorActivity::class.java)).use{scenario->editorReady(scenario);scenario.onActivity{assertNull(editorModel(it).draft!!.originalText);assertFalse(it.findViewById<Button>(R.id.editor_compare).isEnabled)}}
    }
    @Test fun draftComparisonFiltersAndProcessLossNeverRereadBaseline() {
        val token=DraftComparisonInbox.put("notes.md","a \tb\r\n","ab\n")!!
        ActivityScenario.launch<ComparisonActivity>(Intent(context,ComparisonActivity::class.java).putExtra("draftToken",token)).use{scenario->
            comparisonReady(scenario)
            scenario.onActivity{assertTrue(comparisonModel(it).draftMode);assertFalse(it.findViewById<Button>(R.id.compare_left).isEnabled);it.findViewById<CheckBox>(R.id.compare_ignore_space).performClick()}
            comparisonReady(scenario)
            scenario.onActivity{it.findViewById<CheckBox>(R.id.compare_ignore_endings).performClick()}
            comparisonReady(scenario);scenario.recreate();comparisonReady(scenario)
            val state=Bundle()
            scenario.onActivity{assertTrue(comparisonModel(it).result!!.identical);assertTrue(it.findViewById<TextView>(R.id.compare_status).text.contains(context.getString(R.string.compare_equivalent)));comparisonModel(it).save(state)}
            assertFalse(state.toString().contains("a \\tb"))
            var fresh:ComparisonSession?=null
            instrumentation.runOnMainSync{fresh=ComparisonSession(context.applicationContext as Application);fresh!!.restore(state)}
            await{!fresh!!.busy};assertNull(fresh!!.left);assertNull(fresh!!.right);assertEquals(ComparisonFailure.LOST_DRAFT,fresh!!.failure)
            instrumentation.runOnMainSync{ComparisonSession::class.java.getDeclaredMethod("onCleared").apply{isAccessible=true}.invoke(fresh)}
        }
    }
    @Test fun exactPatchPickerIgnoresViewFiltersAndPreservesBothInputs() {
        outputFixture("changes.patch")
        val left=fixture("patch-left.txt","a \tb\r\n");val right=fixture("patch-right.txt","ab\n")
        val filter=IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("text/x-diff")}
        val monitor=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(target("changes.patch"))),true)
        try{ActivityScenario.launch<ComparisonActivity>(Intent(context,ComparisonActivity::class.java).putExtra("uri",left.toString())).use{scenario->
            await{var ready=false;scenario.onActivity{ready=comparisonModel(it).left!=null&&!comparisonModel(it).busy};ready}
            scenario.onActivity{comparisonModel(it).load(false,ComparisonRef(right.toString(),"right.txt"))};comparisonReady(scenario)
            await{var visible=false;scenario.onActivity{
                val back=ComparisonActivity::class.java.getDeclaredField("back").apply{isAccessible=true}.get(it) as Button
                val rect=android.graphics.Rect();val frame=android.graphics.Rect();back.getWindowVisibleDisplayFrame(frame)
                visible=back.height>0&&back.getGlobalVisibleRect(rect)&&rect.height()==back.height&&rect.bottom<=frame.bottom
            };visible}
            device.waitForIdle();assertTrue(device.takeScreenshot(File(context.cacheDir,"roadmap-comparison.png")))
            scenario.onActivity{comparisonModel(it).setOptions(DiffOptions(true,true))};comparisonReady(scenario)
            scenario.onActivity{it.findViewById<Button>(R.id.compare_export).performClick()}
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.compare_export_exact))),5000));click(R.string.done)
            await{var saved=false;scenario.onActivity{saved=ViewModelProvider(it)[OutputSession::class.java].status.result==OutputResult.SAVED};saved}
            val patch=context.contentResolver.openInputStream(target("changes.patch"))!!.use{it.readBytes().toString(Charsets.UTF_8)}
            assertEquals(UnifiedDiff.export(DocumentRepository(context).read(left,null,android.os.CancellationSignal()),DocumentRepository(context).read(right,null,android.os.CancellationSignal())),patch)
            assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.editor_open_saved))),5000))
            scenario.recreate();comparisonReady(scenario)
            assertEquals("a \tb\r\n",context.contentResolver.openInputStream(left)!!.use{it.readBytes().toString(Charsets.UTF_8)})
            assertEquals("ab\n",context.contentResolver.openInputStream(right)!!.use{it.readBytes().toString(Charsets.UTF_8)})
        }}finally{instrumentation.removeMonitor(monitor)}
    }
    @Test fun patchOutputRefusesEitherOriginalAsTarget() {
        val left=fixture("protected-left.txt","left");val right=fixture("protected-right.txt","right")
        val output=OutputSession(context.applicationContext as Application)
        instrumentation.runOnMainSync{output.begin(OutputRequest("changes.patch","text/x-diff","patch",left.toString(),protectedSources=setOf(right.toString())));output.pickerResult(right)}
        await{output.status.phase==OutputPhase.COMPLETE};assertEquals(OutputResult.REFUSED,output.status.result)
        assertEquals("right",context.contentResolver.openInputStream(right)!!.use{it.readBytes().toString(Charsets.UTF_8)})
        instrumentation.runOnMainSync{OutputSession::class.java.getDeclaredMethod("onCleared").apply{isAccessible=true}.invoke(output)}
    }
    @Test fun collectionsRetainReadGrantAfterHistoryRemovalUntilLastOwnerLeaves() {
        val uri=fixture("collection-grant.txt","reference")
        context.contentResolver.call(uri,"grant",uri.toString(),null)
        val repository=DocumentRepository(context);repository.retainReadAccess(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);repository.remember(uri,"reference")
        val store=ReadingCollections(context);val first=store.create("First")!!;val second=store.create("Second")!!
        assertTrue(store.add(first,CollectionMember(uri.toString(),"reference")));assertTrue(store.add(second,CollectionMember(uri.toString(),"reference")))
        // Simulate process loss between collection metadata and grant-owner bookkeeping.
        context.getSharedPreferences("recent_files",Context.MODE_PRIVATE).edit().remove("collections").commit()
        repository.clear();assertTrue(context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission})
        store.reconcile();assertEquals(setOf(uri.toString()),context.getSharedPreferences("recent_files",Context.MODE_PRIVATE).getStringSet("collections",emptySet()))
        store.delete(first);assertTrue(context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission})
        store.remove(second,uri.toString());assertFalse(context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission})
        assertEquals("reference",context.contentResolver.openInputStream(uri)!!.use{it.readBytes().toString(Charsets.UTF_8)})
    }
    @Test fun collectionsEnforceBoundsDuplicateNamesAndPersistentMetadataOnly() {
        val store=ReadingCollections(context);val id=store.create(" Work ")!!
        assertNull(store.create("work"));assertFalse(store.rename(id," "))
        for(index in 1 until ReadingCollections.MAX_COLLECTIONS)assertNotNull(store.create("Collection $index"))
        assertNull(store.create("overflow"))
        val ids=store.entries().map{it.id};var added=0
        for(collection in ids)for(index in 0 until ReadingCollections.MAX_MEMBERS)if(store.add(collection,CollectionMember("content://synthetic/$added","Document $added")))added++
        assertEquals(ReadingCollections.MAX_TOTAL,added)
        assertFalse(store.add(id,CollectionMember("content://extra","overflow")))
        assertEquals(added,ReadingCollections(context).entries().sumOf{it.members.size})
        assertTrue(store.entries().all{it.members.size<=ReadingCollections.MAX_MEMBERS})
        assertFalse(store.reconnect(id,"content://synthetic/0",CollectionMember("https://example.com","web")))
    }
    @Test fun collectionsNativeUiCreatesRenamesAddsAndRestoresAcrossRecreation() {
        val uri=fixture("collection-ui.txt","source");DocumentRepository(context).remember(uri,"Collection fixture")
        ActivityScenario.launch<CollectionsActivity>(Intent(context,CollectionsActivity::class.java)).use{scenario->
            scenario.onActivity{it.findViewById<Button>(R.id.collection_create).performClick()}
            instrumentation.runOnMainSync{val root=WindowInspector.getGlobalWindowViews().first{it.findViewById<EditText>(R.id.collection_name)!=null};root.findViewById<EditText>(R.id.collection_name).setText("Reading");root.findViewById<Button>(android.R.id.button1).performClick()}
            val named=device.wait(Until.findObject(By.text("Reading")),5000);assertNotNull(named);named!!.click()
            scenario.onActivity{it.findViewById<Button>(R.id.collection_add).performClick()}
            device.wait(Until.findObject(By.text("Collection fixture")),5000)!!.click();device.waitForIdle()
            await{ReadingCollections(context).entries().single().members.size==1};scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("Collection fixture")),5000))
            assertEquals(uri.toString(),ReadingCollections(context).entries().single().members.single().uri)
            await{var visible=false;scenario.onActivity{val back=it.findViewById<Button>(R.id.collection_done);val rect=android.graphics.Rect();visible=back.height>0&&back.getGlobalVisibleRect(rect)&&rect.height()==back.height};visible}
            device.waitForIdle();assertTrue(device.takeScreenshot(File(context.cacheDir,"roadmap-collections.png")))
        }
    }
    @Test fun collectionRenameAndConfirmedReselectKeepOriginalFilesIntact() {
        val old=fixture("collection-old.txt","old source");val replacement=fixture("collection-new.txt","new source")
        val store=ReadingCollections(context);val id=store.create("Before")!!;assertTrue(store.add(id,CollectionMember(old.toString(),"Original member")))
        val filter=IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("*/*")}
        val picker=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(replacement).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)),true)
        try{ActivityScenario.launch<CollectionsActivity>(Intent(context,CollectionsActivity::class.java)).use{scenario->
            device.wait(Until.findObject(By.text("Before")),5000)!!.click();click(R.string.collection_rename)
            instrumentation.runOnMainSync{val root=WindowInspector.getGlobalWindowViews().first{it.findViewById<EditText>(R.id.collection_name)!=null};root.findViewById<EditText>(R.id.collection_name).setText("After");root.findViewById<Button>(android.R.id.button1).performClick()}
            await{store.entries().single().name=="After"}
            device.wait(Until.findObject(By.text("Original member")),5000)!!.click();click(R.string.collection_reselect)
            await{picker.hits>0};click(R.string.done)
            await{store.entries().single().members.single().uri==replacement.toString()};scenario.recreate()
            assertEquals("After",store.entries().single().name)
            assertEquals("old source",context.contentResolver.openInputStream(old)!!.use{it.readBytes().toString(Charsets.UTF_8)})
            assertEquals("new source",context.contentResolver.openInputStream(replacement)!!.use{it.readBytes().toString(Charsets.UTF_8)})
        }}finally{instrumentation.removeMonitor(picker)}
    }
    @Test fun jsonLinesNativeNavigationIncludesInvalidAndBlankLinesWithoutLosingSource() {
        val source="{\"a\":1,\"a\":2}\r\n\r\ninvalid\nnull"
        val uri=fixture("records.ndjson",source)
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_VIEW,uri,context,MainActivity::class.java)).use{scenario->
            await{var ready=false;scenario.onActivity{ready=it.findViewById<WebView>(R.id.reader)?.tag==true};ready}
            scenario.onActivity{
                val document=ViewModelProvider(it)[ReaderSession::class.java].document!!
                assertEquals(FileKind.JSON_LINES,document.source.kind);assertEquals(source,document.source.text)
                assertEquals(5,document.structure!!.nodes.size)
                MainActivity::class.java.getDeclaredMethod("showStructure",Bundle::class.java).apply{isAccessible=true}.invoke(it,null)
            }
            assertTrue(device.wait(Until.hasObject(By.textContains(context.getString(R.string.jsonl_invalid))),5000))
            assertTrue(device.wait(Until.hasObject(By.textContains(context.getString(R.string.jsonl_blank))),5000))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.textContains(context.getString(R.string.jsonl_invalid))),5000))
            device.waitForIdle();assertTrue(device.takeScreenshot(File(context.cacheDir,"roadmap-json-lines.png")))
        }
    }
    @Test fun savedCopyActionSurvivesRecreationAndKeepsSourceIdentity() {
        outputFixture("reopen.md")
        val filter=IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("text/markdown")}
        val picker=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(target("reopen.md"))),true)
        val captured=AtomicReference<Intent>();val main=capture(MainActivity::class.java.name,captured)
        try{editor("# Original").use{scenario->
            editorReady(scenario)
            scenario.onActivity{it.findViewById<EditText>(R.id.editor_text).text.append(" edited");it.findViewById<Button>(R.id.editor_save).performClick()}
            click(R.string.choose_destination)
            await{var saved=false;scenario.onActivity{saved=ViewModelProvider(it)[OutputSession::class.java].status.result==OutputResult.SAVED&&!editorModel(it).loading&&!editorModel(it).dirty};saved}
            scenario.recreate();editorReady(scenario);click(R.string.editor_open_saved);await{captured.get()!=null}
            assertEquals(target("reopen.md"),captured.get().data)
            assertEquals(0,captured.get().flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            assertTrue(captured.get().getBooleanExtra(ReaderOpening.RETAIN_READ,false))
            assertEquals("# Original",context.contentResolver.openInputStream(Uri.parse("content://de.utrost.textr.test.documents/roadmap-editor.md"))!!.use{it.readBytes().toString(Charsets.UTF_8)})
            assertEquals("# Original edited",context.contentResolver.openInputStream(target("reopen.md"))!!.use{it.readBytes().toString(Charsets.UTF_8)})
        }}finally{instrumentation.removeMonitor(picker);instrumentation.removeMonitor(main)}
    }
    @Test fun savedCopyReadFailureDoesNotUndoOrMisreportSuccessfulWrite() {
        context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"),"output-fixture","write-only.md",Bundle().apply{putString("operation","configure");putString("mode","read-denied")})
        val filter=IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("text/markdown")}
        val picker=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(target("write-only.md"))),true)
        try{editor("# Saved content").use{scenario->
            editorReady(scenario);scenario.onActivity{it.findViewById<Button>(R.id.editor_save).performClick()};click(R.string.choose_destination)
            await{var saved=false;scenario.onActivity{saved=ViewModelProvider(it)[OutputSession::class.java].status.result==OutputResult.SAVED&&!editorModel(it).loading};saved}
            click(R.string.editor_open_saved)
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.access_denied))),10000))
            val inspected=context.contentResolver.call(Uri.parse("content://de.utrost.textr.test.documents"),"output-fixture","write-only.md",Bundle().apply{putString("operation","inspect")})!!
            assertEquals("# Saved content",inspected.getByteArray("bytes")!!.toString(Charsets.UTF_8))
            assertFalse(EditDraftStore(context).exists())
        }}finally{
            instrumentation.removeMonitor(picker)
            instrumentation.runOnMainSync{androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().forEach{it.finish()}}
        }
    }
    @Test fun cancellingPatchPickerKeepsComparisonSnapshotsFiltersAndNavigation() {
        val token=DraftComparisonInbox.put("notes.md","left\n","right\n")!!
        val filter=IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("text/x-diff")}
        val picker=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        try{ActivityScenario.launch<ComparisonActivity>(Intent(context,ComparisonActivity::class.java).putExtra("draftToken",token)).use{scenario->
            comparisonReady(scenario);scenario.onActivity{comparisonModel(it).setOptions(DiffOptions(ignoreWhitespace=true))};comparisonReady(scenario)
            scenario.onActivity{it.findViewById<Button>(R.id.compare_export).performClick()};click(R.string.done)
            await{picker.hits>0};await{var idle=false;scenario.onActivity{idle=!ViewModelProvider(it)[OutputSession::class.java].busy&&!comparisonModel(it).busy};idle}
            scenario.onActivity{assertEquals("left\n",comparisonModel(it).left!!.document.text);assertEquals("right\n",comparisonModel(it).right!!.document.text);assertTrue(comparisonModel(it).options.ignoreWhitespace);assertNotNull(comparisonModel(it).result)}
        }}finally{instrumentation.removeMonitor(picker)}
    }
    @Test fun darkLargeTextTasksRemainReadOnlyAndReaderControlsReachable() {
        val text="# Reading checklist\n\n- [ ] Pending task\n- [x] Completed task\n  - [X] Nested task\n"
        device.executeShellCommand("settings put system font_scale 2.0")
        context.getSharedPreferences("reading",0).edit().putString("theme","dark").commit()
        try{
            val uri=fixture("large-tasks.md",text)
            ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_VIEW,uri,context,MainActivity::class.java)).use{scenario->
                await{var ready=false;scenario.onActivity{ready=it.findViewById<WebView>(R.id.reader)?.tag==true};ready}
                scenario.onActivity{
                    val prepared=ViewModelProvider(it)[ReaderSession::class.java].document!!
                    assertEquals(text,prepared.source.text);assertTrue(prepared.preview!!.contains("Unchecked task"));assertTrue(prepared.preview!!.contains("Checked task"));assertFalse(prepared.preview!!.contains("<input"))
                    val web=it.findViewById<WebView>(R.id.reader);assertFalse(web.settings.javaScriptEnabled);assertTrue(web.height>0)
                    fun menu(view:View):View? {
                        if(view.contentDescription==context.getString(R.string.more_options))return view
                        if(view is android.view.ViewGroup)for(index in 0 until view.childCount)menu(view.getChildAt(index))?.let{return it}
                        return null
                    }
                    val control=menu(it.window.decorView)!!;val rect=android.graphics.Rect();assertTrue(control.getGlobalVisibleRect(rect));assertEquals(control.height,rect.height())
                }
                device.waitForIdle();assertTrue(device.takeScreenshot(File(context.cacheDir,"roadmap-tasks-dark-large.png")))
            }
            val id=ReadingCollections(context).create("Large text")!!
            ReadingCollections(context).add(id,CollectionMember(uri.toString(),"Checklist"))
            ActivityScenario.launch<CollectionsActivity>(Intent(context,CollectionsActivity::class.java)).use{scenario->
                device.wait(Until.findObject(By.text("Large text")),5000)!!.click();device.waitForIdle()
                await{var visible=false;scenario.onActivity{val done=it.findViewById<Button>(R.id.collection_done);val rect=android.graphics.Rect();visible=done.height>0&&done.getGlobalVisibleRect(rect)&&rect.height()==done.height};visible}
                device.pressKeyCode(KeyEvent.KEYCODE_TAB)
                await{var focused=false;scenario.onActivity{focused=it.currentFocus!=null};focused}
            }
        }finally{device.executeShellCommand("settings put system font_scale 1.0")}
    }
    private fun await(condition:()->Boolean){val end=System.nanoTime()+30_000_000_000L;while(System.nanoTime()<end){if(condition())return;Thread.sleep(50)};fail("Roadmap completion condition timed out")}
}

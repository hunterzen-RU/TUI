package ohi.andre.consolelauncher.managers.plugins

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.managers.PluginManager
import ohi.andre.consolelauncher.ui.theme.TuiTheme
import java.io.File

class PluginStudioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialPluginId = intent.getStringExtra("open_plugin")

        setContent {
            TuiTheme {
                PluginStudioScreen(
                    initialPluginId = initialPluginId,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStudioScreen(
    initialPluginId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginManager = remember { PluginManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val installedPlugins = remember { mutableStateListOf<PluginModel>() }
    var currentPlugin by remember { mutableStateOf<PluginModel?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun refreshPlugins(selectId: String? = null) {
        val list = pluginManager.installedPlugins
        installedPlugins.clear()
        installedPlugins.addAll(list)

        val target = if (selectId != null) {
            list.firstOrNull { it.id.equals(selectId, ignoreCase = true) }
        } else {
            currentPlugin?.let { cp -> list.firstOrNull { it.id == cp.id } } ?: list.firstOrNull()
        }
        currentPlugin = target
    }

    LaunchedEffect(Unit) {
        refreshPlugins(initialPluginId)
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddFileDialog by remember { mutableStateOf(false) }
    var isPluginMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { isPluginMenuExpanded = true },
                            label = {
                                Text(
                                    text = currentPlugin?.name ?: "No Plugin Selected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        DropdownMenu(
                            expanded = isPluginMenuExpanded,
                            onDismissRequest = { isPluginMenuExpanded = false }
                        ) {
                            installedPlugins.forEach { plugin ->
                                DropdownMenuItem(
                                    text = { Text("${plugin.name} (${plugin.id})") },
                                    onClick = {
                                        currentPlugin = plugin
                                        isPluginMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            currentPlugin?.let { p ->
                                p.saveManifest()
                                pluginManager.launchPlugin(context, p, emptyArray(), object : PluginManager.Callback<String> {
                                    override fun onSuccess(result: String?) {
                                        scope.launch { snackbarHostState.showSnackbar("Plugin executed successfully.") }
                                    }

                                    override fun onError(message: String?) {
                                        scope.launch { snackbarHostState.showSnackbar("Error: $message") }
                                    }
                                })
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test / Run", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            currentPlugin?.let { p ->
                                val zip = pluginManager.exportPluginZip(p)
                                scope.launch {
                                    if (zip != null && zip.exists()) {
                                        snackbarHostState.showSnackbar("Exported to ${zip.name}")
                                    } else {
                                        snackbarHostState.showSnackbar("Export failed")
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export ZIP", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Material 3 Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Code Editor", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manifest", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Files", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Templates", style = MaterialTheme.typography.labelMedium) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> StudioEditorTab(
                    plugin = currentPlugin,
                    onSave = { scope.launch { snackbarHostState.showSnackbar("File saved successfully.") } }
                )
                1 -> StudioManifestTab(
                    plugin = currentPlugin,
                    onUpdated = { scope.launch { snackbarHostState.showSnackbar("Manifest updated.") } }
                )
                2 -> StudioFilesTab(
                    plugin = currentPlugin,
                    onAddFile = { showAddFileDialog = true },
                    onRefresh = { refreshPlugins() }
                )
                3 -> StudioTemplatesTab(
                    pluginManager = pluginManager,
                    onTemplateChosen = { template ->
                        val id = "${template.id}_app"
                        val ok = pluginManager.createPlugin(id, template.name, template.id, template.description)
                        if (ok) {
                            refreshPlugins(id)
                            selectedTab = 0
                            scope.launch { snackbarHostState.showSnackbar("Created plugin from ${template.name}") }
                        }
                    }
                )
            }
        }
    }

    // Create New Plugin Dialog
    if (showCreateDialog) {
        CreatePluginDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { id, name, template ->
                val ok = pluginManager.createPlugin(id, name, template, "Custom T-UI Plugin")
                if (ok) {
                    refreshPlugins(id)
                    selectedTab = 0
                    scope.launch { snackbarHostState.showSnackbar("Created $name ($id)") }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Creation failed. ID may exist.") }
                }
                showCreateDialog = false
            }
        )
    }

    // Add File Dialog
    if (showAddFileDialog && currentPlugin != null) {
        AddFileDialog(
            onDismiss = { showAddFileDialog = false },
            onAdd = { fileName ->
                val f = File(currentPlugin!!.folder, fileName)
                currentPlugin!!.writeFile(f, "// $fileName\n")
                showAddFileDialog = false
                refreshPlugins()
                scope.launch { snackbarHostState.showSnackbar("Added $fileName") }
            }
        )
    }
}

@Composable
fun StudioEditorTab(
    plugin: PluginModel?,
    onSave: () -> Unit
) {
    if (plugin == null) {
        EmptyStudioState(message = "Select or create a plugin to begin editing.")
        return
    }

    val files = remember(plugin) { plugin.listFiles() }
    var selectedFile by remember(plugin) {
        mutableStateOf(files.firstOrNull { it.name == plugin.manifest.entryPoint } ?: files.firstOrNull())
    }
    var codeContent by remember(selectedFile) {
        mutableStateOf(if (selectedFile != null) plugin.readFile(selectedFile) else "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Horizontal File Selector Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(files) { file ->
                val isSelected = file == selectedFile
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedFile = file
                        codeContent = plugin.readFile(file)
                    },
                    label = { Text(plugin.getRelativePath(file), style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Editor Card with Save button
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFile?.let { plugin.getRelativePath(it) } ?: "No File",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = {
                            selectedFile?.let { file ->
                                plugin.writeFile(file, codeContent)
                                onSave()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = codeContent,
                    onValueChange = { codeContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun StudioManifestTab(
    plugin: PluginModel?,
    onUpdated: () -> Unit
) {
    if (plugin == null) {
        EmptyStudioState(message = "Select a plugin to configure manifest.")
        return
    }

    val manifest = remember(plugin) { plugin.manifest }
    var name by remember(manifest) { mutableStateOf(manifest.name) }
    var version by remember(manifest) { mutableStateOf(manifest.version) }
    var targetApp by remember(manifest) { mutableStateOf(manifest.targetAppVersion) }
    var desc by remember(manifest) { mutableStateOf(manifest.description) }
    var entryPoint by remember(manifest) { mutableStateOf(manifest.entryPoint) }
    var perms by remember(manifest) { mutableStateOf(manifest.permissions.joinToString(", ")) }
    var activity by remember(manifest) {
        mutableStateOf(manifest.manifestSpecials["activity"]?.toString() ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("MANIFEST SPECIFICATION", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Plugin Display Name") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("Version") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = targetApp,
                onValueChange = { targetApp = it },
                label = { Text("Target App") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Description") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        OutlinedTextField(
            value = entryPoint,
            onValueChange = { entryPoint = it },
            label = { Text("Entry Point (start.kt / start.java / index.html)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = perms,
            onValueChange = { perms = it },
            label = { Text("Permissions (comma-separated)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = activity,
            onValueChange = { activity = it },
            label = { Text("Manifest Specials: Activity Host") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = {
                manifest.name = name.trim()
                manifest.version = version.trim()
                manifest.targetAppVersion = targetApp.trim()
                manifest.description = desc.trim()
                manifest.entryPoint = entryPoint.trim()

                manifest.permissions.clear()
                perms.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    manifest.permissions.add(it)
                }

                if (activity.trim().isNotEmpty()) {
                    manifest.manifestSpecials["activity"] = activity.trim()
                } else {
                    manifest.manifestSpecials.remove("activity")
                }

                plugin.saveManifest()
                onUpdated()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Apply Manifest Changes", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun StudioFilesTab(
    plugin: PluginModel?,
    onAddFile: () -> Unit,
    onRefresh: () -> Unit
) {
    if (plugin == null) {
        EmptyStudioState(message = "Select a plugin to view files.")
        return
    }

    val files = remember(plugin) { plugin.listFiles() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${files.size} files in bundle", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            FilledTonalButton(
                onClick = onAddFile,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add File")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(files) { file ->
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Menu else Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = plugin.getRelativePath(file),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${file.length()} bytes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                file.delete()
                                onRefresh()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioTemplatesTab(
    pluginManager: PluginManager,
    onTemplateChosen: (PluginTemplate) -> Unit
) {
    val templates = remember { mutableStateListOf<PluginTemplate>() }
    var isLoading by remember { mutableStateOf(true) }

    fun load() {
        isLoading = true
        pluginManager.fetchTemplates(object : PluginManager.Callback<List<PluginTemplate>> {
            override fun onSuccess(result: List<PluginTemplate>?) {
                isLoading = false
                templates.clear()
                if (result != null) templates.addAll(result)
            }

            override fun onError(message: String?) {
                isLoading = false
            }
        })
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMMUNITY TEMPLATES",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(templates) { template ->
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "v${template.version}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Author: ${template.author} • ID: ${template.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = template.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { onTemplateChosen(template) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Plugin From Template", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStudioState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CreatePluginDialog(
    onDismiss: () -> Unit,
    onCreate: (id: String, name: String, template: String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("kt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Create New T-UI Plugin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Plugin ID (e.g. system_stats)") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plugin Name (e.g. System Stats)") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Text("Template Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kt" to "Kotlin", "java" to "Java", "html" to "Web HTML").forEach { (k, label) ->
                        FilterChip(
                            selected = selectedTemplate == k,
                            onClick = { selectedTemplate = k },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (id.trim().isNotEmpty()) {
                        onCreate(id.trim(), name.trim(), selectedTemplate)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddFileDialog(
    onDismiss: () -> Unit,
    onAdd: (fileName: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Add File to Plugin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File Name (e.g. helper.kt)") },
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.trim().isNotEmpty()) {
                        onAdd(fileName.trim())
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

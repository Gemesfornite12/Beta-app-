                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.size(8.dp))
                            Text("Mapas y rutas", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ChevronLeft, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        if (navigationMode) {
            // Navigation uses the whole screen for the map, with only the
            // essential Google Maps-style guidance and trip controls over it.
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = Color(0xFF064E3B),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Text(
                            "Navegación activa",
                            color = Color.White.copy(alpha = .8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            instructions.getOrNull(navigationStep) ?: "Sigue la ruta",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF0F172A).copy(alpha = .97f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        summary?.let { s ->
                            val distance = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0)
                            val duration = "${(s.duration / 60.0).roundToInt()} min"
                            val arrivalTime = remember(s) {
                                java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                                    java.util.Date(System.currentTimeMillis() + s.duration.toLong() * 1000L)
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(duration, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Tiempo", color = Color.LightGray, fontSize = 12.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(distance, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Distancia", color = Color.LightGray, fontSize = 12.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(arrivalTime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Llegada", color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                navigationMode = false
                                navigationStep = 0
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Finalizar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Surface(
                    color = if (hasRealLocation) Color(0xFF14532D) else Color(0xFF334155),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("Tu ubicación actual", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(locationLabel, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(color = Color(0xFF1E293B), shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Destino") },
                            placeholder = { Text("Ej. Parque Central de Heredia o toca el mapa") }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Button(onClick = { profileMenuOpen = true }) {
                                    Text(selectedProfile.second)
                                }
                                DropdownMenu(profileMenuOpen, { profileMenuOpen = false }) {
                                    profiles.forEach { profile ->
                                        DropdownMenuItem(
                                            text = { Text(profile.second) },
                                            onClick = {
                                                selectedProfile = profile
                                                profileMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = ::calculateRoute,
                                enabled = destination.isNotBlank() && !loading
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Calcular ruta")
                                }
                            }
                        }
                        Text("Inicio: $locationLabel", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        errorText?.let { Text(it, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 6.dp)) }
                        summary?.let { s ->
                            val distance = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0)
                            val duration = "${(s.duration / 60.0).roundToInt()} min"
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$distance • $duration • ${selectedProfile.second}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    navigationMode = true
                                    navigationStep = 0
                                }) {
                                    Text("Navegar")
                                }
                            }
                        }
                        if (instructions.isNotEmpty()) {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.height(110.dp)
                            ) {
                                items(instructions) {
                                    Text("• $it", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

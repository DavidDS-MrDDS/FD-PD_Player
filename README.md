# FD-PD Player

FD-PD Player es una aplicación Android de reproducción de música local.  
La app permite escuchar canciones almacenadas en el dispositivo, organizarlas en listas, marcar favoritos, ocultar canciones y compartir favoritos mediante una API propia.

Como se puede apreciar, en la carpeta principal hemos dejado el APK de la última versión de la aplicación.

## Características principales

- Reproducción de música local.
- Minireproductor integrado en la aplicación.
- Reproducción en segundo plano.
- Notificación multimedia con controles de reproducción.
- Búsqueda de canciones locales.
- Ordenación de canciones.
- Creación y gestión de listas de reproducción.
- Sistema de canciones favoritas.
- Sincronización de favoritos con una API.
- Búsqueda de favoritos públicos de otros usuarios.
- Descarga de canciones públicas compartidas.
- Sistema de canciones ocultas.
- Registro e inicio de sesión de usuarios.

## Fragmentos principales

La aplicación está organizada en varias pantallas principales:

### Canciones

Es la pantalla principal de la app.  
Muestra las canciones almacenadas en el dispositivo y permite reproducirlas, buscarlas, ordenarlas, recargar la lista y acceder a sus opciones.

Desde esta pantalla también se pueden ocultar canciones para que no aparezcan en la lista principal.

### Listas

Permite crear listas de reproducción personalizadas.  
El usuario puede guardar canciones dentro de una lista y reproducir solamente las canciones que pertenecen a esa lista.

### Búsqueda

Muestra canciones favoritas públicas subidas por otros usuarios.  
Desde este apartado se puede buscar entre los favoritos públicos y descargar canciones compartidas.

### Ajustes

Contiene opciones generales de la aplicación, como iniciar sesión, acceder a canciones ocultas, organizar almacenamiento, usar la reproducción temporal y consultar información de contacto.

### Favoritos

Muestra las canciones favoritas del usuario.  
Cuando el usuario inicia sesión, sus favoritos pueden sincronizarse con la API para guardarse y mostrarse también desde el sistema remoto.

## Funcionamiento general

La aplicación carga las canciones locales del dispositivo y las muestra en una lista principal.  
Desde esa lista el usuario puede reproducir canciones, ordenarlas, buscarlas, añadirlas a listas u ocultarlas.

Las listas permiten una reproducción separada de la principal, pudiendo elegir reproducir solamente las canciones de una lista concreta.

El reproductor permite seguir escuchando música aunque la aplicación esté en segundo plano, sin tener que mantener abierto el fragmento de reproducción.  
Además, se muestra una notificación multimedia con controles para pausar, continuar, avanzar, retroceder o cerrar la reproducción.

La app cuenta con un sistema de usuarios. Al iniciar sesión, el usuario puede subir sus canciones favoritas para que aparezcan de forma pública y también puede descargar canciones favoritas de otros usuarios.

## API y Supabase

Repositorio de la API: `https://github.com/DavidDS-MrDDS/FD-PD_Player_API/tree/main`

La aplicación utiliza una API propia para gestionar usuarios y favoritos.  
Esta API permite registrar usuarios, iniciar sesión, obtener favoritos, subir canciones favoritas y consultar favoritos públicos.

Supabase se utiliza como base de datos y almacenamiento remoto.  
La base de datos guarda la información de usuarios y canciones favoritas, mientras que Supabase Storage almacena los archivos de audio subidos por los usuarios.

La comunicación entre la app y la API se realiza mediante peticiones HTTP.  
Para las funciones privadas, como subir o eliminar favoritos, se usa un token de sesión para comprobar que el usuario ha iniciado sesión correctamente.

## Tecnologías usadas

- Android Studio (Aplicación)
- Room (Almacenamiento local de las diferentes listas)
- ViewBinding (Conexión entre los XML y los Java)
- RecyclerView (Listado de objetos)
- Material Components (Diseño de botones, tarjetas y ventanas)
- MediaPlayer (Reproducción de música)
- MediaSession (Notificación multimedia y controles del reproductor)
- DownloadManager (Descarga de canciones compartidas)
- Retrofit (Comunicación con la API)
- Supabase (Base de datos y almacenamiento remoto)
- Supabase Storage (Almacenamiento remoto de archivos de audio)
- Vercel (Despliegue de la API)

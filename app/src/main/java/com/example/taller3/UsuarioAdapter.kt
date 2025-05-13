package com.example.taller3

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.taller3.model.Usuario

class UsuarioAdapter(
    context: Context,
    private val resource: Int,
    private val usuarios: List<Usuario>
) : ArrayAdapter<Usuario>(context, resource, usuarios) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(resource, parent, false)

        val usuario = usuarios[position]

        val imagenUsuario = view.findViewById<ImageView>(R.id.ImagenUsuario)
        val textNombre = view.findViewById<TextView>(R.id.textNombre)
        val buttonLocation = view.findViewById<ImageButton>(R.id.buttonLocation)

        textNombre.text = "${usuario.nombre} ${usuario.apellido}"

        if (!usuario.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(usuario.imageUrl)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .circleCrop()
                .into(imagenUsuario)
        } else {
            Glide.with(context)
                .load(R.mipmap.ic_launcher_round)
                .circleCrop()
                .into(imagenUsuario)
        }

        buttonLocation.setOnClickListener {
            Toast.makeText(context, "Ver ubicación de ${usuario.nombre}", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, MapaAmigoActivity::class.java)
            // Usamos uidTransient, que habremos poblado con la clave del snapshot
            intent.putExtra("USER_ID_SELECCIONADO", usuario.uidTransient)
            intent.putExtra("USER_LATITUD_SELECCIONADO", usuario.latitud)
            intent.putExtra("USER_LONGITUD_SELECCIONADO", usuario.longitud)
            intent.putExtra("USER_NOMBRE_SELECCIONADO", "${usuario.nombre} ${usuario.apellido}")
            context.startActivity(intent)
        }
        return view
    }
}
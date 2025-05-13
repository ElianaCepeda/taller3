package com.example.taller3.model;

import com.google.firebase.database.Exclude; // Importante para el UID transitorio

public class Usuario {
    private String nombre;
    private String apellido;
    private String numeroIdentificacion;
    private String disponibilidad;
    private String imageUrl;
    private double latitud;
    private double longitud;

    // Este campo UID no se guardará en Firebase, pero lo usaremos en la app.
    // Se poblará a partir de la clave del DataSnapshot.
    @Exclude // Firebase no intentará mapear este campo desde/hacia la DB
    private String uidTransient;


    public Usuario() {
        this.disponibilidad= "Disponible";
    }

    // Getters y Setters para los campos que SÍ están en Firebase
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getNumeroIdentificacion() {
        return numeroIdentificacion;
    }

    public void setNumeroIdentificacion(String numeroIdentificacion) {
        this.numeroIdentificacion = numeroIdentificacion;
    }

    public String getDisponibilidad() {
        return disponibilidad;
    }

    public void setDisponibilidad(String disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public double getLatitud() {
        return latitud;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }

    // Getter y Setter para el UID que usaremos en la app (no persistido directamente)
    @Exclude
    public String getUidTransient() {
        return uidTransient;
    }

    @Exclude
    public void setUidTransient(String uid) {
        this.uidTransient = uid;
    }
}

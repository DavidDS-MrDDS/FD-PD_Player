package es.iesagora.fd_pdplayer.funcionamiento.controlAlmacenamiento;

public class ResultadoOrganizacion {

    public int movidas;
    public int yaOrganizadas;
    public int fallidas;

    public String crearMensaje() {
        return "Organización terminada.\n\n"
                + "Movidas: " + movidas + "\n"
                + "Ya organizadas: " + yaOrganizadas + "\n"
                + "Fallidas: " + fallidas;
    }

    public ResultadoOrganizacion copia() {
        ResultadoOrganizacion copia = new ResultadoOrganizacion();
        copia.movidas = movidas;
        copia.yaOrganizadas = yaOrganizadas;
        copia.fallidas = fallidas;
        return copia;
    }
}
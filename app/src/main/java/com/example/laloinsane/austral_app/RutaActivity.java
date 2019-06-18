package com.example.laloinsane.austral_app;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.example.laloinsane.austral_app.API.APIService;
import com.example.laloinsane.austral_app.Models.Conexion;
import com.example.laloinsane.austral_app.Models.Unidad;
import com.example.laloinsane.austral_app.Models.UnidadNodos;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

import nose.Nodo;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RutaActivity extends AppCompatActivity {

    private Retrofit retrofit;
    /*private TextView id_test_distancia;
    private TextView id_test_ruta;*/

    //osmdroid
    MapView map = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Obtencion de los datos del Activity anterior
        Bundle extras = getIntent().getExtras();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("" + extras.getInt("unidad_id"));

        //osmdroid
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_ruta);

        //osmdroid
        map = (MapView) findViewById(R.id.map_ruta);
        map.setTileSource(TileSourceFactory.MAPNIK);
        //osmdroid
        IMapController mapController = map.getController();
        mapController.setZoom(17.5);
        //osmdroid
        GeoPoint startPoint = new GeoPoint(extras.getDouble("latitud"), extras.getDouble("longitud"));
        mapController.setCenter(startPoint);

        retrofit = new Retrofit.Builder()
                .baseUrl("base_url")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        obtenerDatos(extras.getInt("unidad_id"), extras.getInt("campus"));
    }

    private void obtenerDatos(final int id_unidad,final int id_campus){
        APIService api = retrofit.create(APIService.class);
        //Call<UnidadNodos> call = api.getUnidad(1, 2);
        Call<UnidadNodos> call = api.getUnidad(id_campus, id_unidad);

        call.enqueue(new Callback<UnidadNodos>() {
            @Override
            public void onResponse(Call<UnidadNodos> call, Response<UnidadNodos> response) {
                if(response.isSuccessful()){
                    UnidadNodos apiRespuesta = response.body();
                    ArrayList<Conexion> datos= apiRespuesta.getConexiones();
                    ArrayList<com.example.laloinsane.austral_app.Models.Nodo> datos_nodos= apiRespuesta.getNodos();
                    List<Nodo> lista_nodos_ejemplo = new ArrayList<nose.Nodo>();

                    for (com.example.laloinsane.austral_app.Models.Nodo nodo : datos_nodos) {
                        ArrayList<nose.Conexion> conexion_nueva = new ArrayList<nose.Conexion>();
                        List<Conexion> con = nodo.getConexiones();
                        if (con.size() != 0) {
                            for (Conexion co : con) {
                                nose.Conexion n_c = new nose.Conexion(co.getDestino(), co.getDistancia());
                                conexion_nueva.add(n_c);
                            }
                        }

                        nose.Nodo nodo_nuevo = new nose.Nodo(nodo.getId_nodo(), nodo.getId_campus(), nodo.getLatitud_nodo(), nodo.getLongitud_nodo(), conexion_nueva);
                        lista_nodos_ejemplo.add(nodo_nuevo);
                    }

                    nose.Vertice[] vertices = new nose.Vertice[lista_nodos_ejemplo.size()];
                    for(int i = 0; i < vertices.length; i++){
                        vertices[i] = new nose.Vertice(lista_nodos_ejemplo.get(i).getId_nodo());

                        List<nose.Conexion> con = lista_nodos_ejemplo.get(i).getConexiones();
                        for(int x = 0; x < con.size(); x++){
                            vertices[i].add_arista(lista_nodos_ejemplo.get(i).getId_nodo(), con.get(x).getDestino(), con.get(x).getDistancia());
                        }
                    }

                    double[][] matriz_adyacencia = new double[vertices.length][vertices.length];
                    for(int i = 0; i < vertices.length; i++){
                        for(int j = 0; j < vertices.length; j++){
                            boolean is_empty = false;
                            double distancia = 0;

                            ArrayList<nose.Arista> arr = vertices[i].getAristas();
                            for(int x = 0; x < arr.size(); x++){
                                if(vertices[j].getId_vertice() == arr.get(x).getId_vertice_destino()){
                                    is_empty = true;
                                    distancia = arr.get(x).getDistancia();
                                }
                            }
                            if(is_empty == true){
                                matriz_adyacencia[i][j] = distancia;
                            }else{
                                matriz_adyacencia[i][j] = 0;
                            }
                        }
                    }

                    nose.Grafo b = new nose.Grafo(vertices, matriz_adyacencia);
                    nose.direccion hol = b.camino_mas_corto(4, datos.get(0).getDestino());
                    //nose.direccion hol = b.camino_mas_corto(3, datos.get(0).getDestino());
                    //nose.direccion hol = b.camino_mas_corto(3, 2);

                    ArrayList<Integer> ruta = hol.getRuta();

                    List<GeoPoint> geoPoints = new ArrayList<>();

                    for(int i= 0; i<ruta.size(); i++){
                        for (com.example.laloinsane.austral_app.Models.Nodo nodo : datos_nodos) {
                            if(ruta.get(i) == nodo.getId_nodo()){
                                GeoPoint gpt = new GeoPoint(nodo.getLatitud_nodo(), 	nodo.getLongitud_nodo());
                                geoPoints.add(gpt);
                            }
                        }
                    }

                    Polyline line = new Polyline();   //see note below!
                    line.setPoints(geoPoints);
                    map.getOverlayManager().add(line);

                }else{
                    Log.e("TIPO_MENU", " onResponse: " + response.errorBody());
                }
            }
            @Override
            public void onFailure(Call<UnidadNodos> call, Throwable t) {
                Log.e("TIPO_MENU", " onFailure: " + t.getMessage());
            }
        });
    }

    //osmdroid
    public void onResume() {
        super.onResume();
        map.onResume();
    }
    //osmdroid
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}

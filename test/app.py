import os

from flask import Flask, render_template, jsonify
import requests

app = Flask(__name__)

# --- ENDPOINTS (Tu propia API que consume las del Estado) ---

@app.route('/api/bcrp')
def obtener_bcrp():
    url = "https://estadisticas.bcrp.gob.pe/estadisticas/series/api/PD04637PD/json/2026-08-01/2026-08-31"
    respuesta = requests.get(url)
    return jsonify(respuesta.json())

@app.route('/api/igp')
def obtener_igp():
    try:
        url = "https://ide.igp.gob.pe/arcgis/rest/services/monitoreocensis/SismosReportados/MapServer/0/query?where=1%3D1&outFields=*&orderByFields=fecha%20DESC&resultRecordCount=10&f=json"
        # Cabecera para evitar bloqueos por WAF del gobierno
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"}
        
        # verify=False permite saltar bloqueos por certificados SSL caducados
        respuesta = requests.get(url, headers=headers, verify=False) 
        
        return jsonify(respuesta.json())
    except Exception as e:
        # Si algo falla en Python, lo enviamos al frontend para saber qué pasó
        return jsonify({"error": str(e)}), 500



# --- NUEVO ENDPOINT PARA EL MEF ---
@app.route('/api/mef')
def obtener_mef():
    try:
        # URL de Datos Abiertos del MEF
        url = "https://www.datosabiertos.gob.pe/api/3/action/datastore_search?resource_id=73989c0b-ffc8-4721-a53c-a9eb82103f69&limit=10"
        
        # Cabeceras reforzadas para evadir bloqueos tipo WAF o Cloudflare
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "es-PE,es;q=0.9,en-US;q=0.8,en;q=0.7"
        }
        
        # timeout=10 evita que se quede colgado esperando
        respuesta = requests.get(url, headers=headers, verify=False, timeout=10) 
        
        # Validamos si la respuesta realmente es un JSON antes de procesarla
        try:
            datos_json = respuesta.json()
            return jsonify(datos_json)
        except Exception:
            # Si falla, imprimimos en la terminal qué nos respondió el gobierno realmente
            print("ERROR: El servidor del Estado devolvió HTML en lugar de JSON. Fragmento de la respuesta:")
            print(respuesta.text[:300]) 
            return jsonify({"error": "El servidor del Estado bloqueó la conexión o está en mantenimiento."}), 502

    except Exception as e:
        print(f"Error interno en Python: {str(e)}")
        return jsonify({"error": str(e)}), 500



# --- NUEVOS ENDPOINTS: SECTORES ECONÓMICOS ---
'''
@app.route('/api/ingemmet')
def obtener_ingemmet():
    try:
        # Capa de Datos Abiertos de INGEMMET (Concesiones Mineras)
        url = "https://geocatminapp.ingemmet.gob.pe/arcgis/rest/services/Servicios_Geocatmin/Datos_Abiertos/MapServer/0/query?where=1%3D1&outFields=*&f=json&resultRecordCount=3"
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        respuesta = requests.get(url, headers=headers, verify=False, timeout=10)
        return jsonify(respuesta.json())
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/osinergmin')
def obtener_osinergmin():
    try:
        # Capa de Concesiones Eléctricas de OSINERGMIN
        url = "https://ws5.osinergmin.gob.pe/igserver/rest/services/Electricidad/Zonas_Concesion_Elec/MapServer/0/query?where=1%3D1&outFields=*&f=json&resultRecordCount=3"
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        respuesta = requests.get(url, headers=headers, verify=False, timeout=10)
        return jsonify(respuesta.json())
    except Exception as e:
        return jsonify({"error": str(e)}), 500
'''
from flask import request




@app.route('/api/oefa')
def obtener_oefa():
    try:
        # Consulta directamente el catálogo oficial de OEFA en la plataforma nacional
        # Este endpoint busca los paquetes y datasets publicados por OEFA
        url = "https://www.datosabiertos.gob.pe/api/3/action/package_search?q=oefa+multas&rows=5"
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json"
        }
        
        respuesta = requests.get(url, headers=headers, verify=False, timeout=15)
        
        if respuesta.status_code == 200:
            datos = respuesta.json()
            return jsonify({
                "estado": "exito",
                "total_datasets_encontrados": datos.get("result", {}).get("count", 0),
                "datasets": datos.get("result", {}).get("results", [])
            })
        else:
            return jsonify({"error": "No se pudo conectar con el catálogo", "status": respuesta.status_code}), respuesta.status_code

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# --- RUTA PARA LA PÁGINA VISUAL ---
@app.route('/pagina-sectores')
def pagina_sectores():
    return render_template('sectores.html')








    

# --- NUEVA RUTA PARA LA PÁGINA DEL MEF ---
@app.route('/pagina-mef')
def pagina_mef():
    return render_template('mef.html')



# --- RUTAS DE LAS PÁGINAS WEB (Frontend) ---

@app.route('/')
def inicio():
    return render_template('index.html')

@app.route('/pagina-bcrp')
def pagina_bcrp():
    return render_template('bcrp.html')

@app.route('/pagina-igp')
def pagina_igp():
    return render_template('igp.html')

if __name__ == '__main__':
    app.run(debug=True)
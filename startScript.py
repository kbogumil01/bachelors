# skrypt do uruchomienia wszystkich mikrousług aplikacji:
# dwie bazy danych postgresql, dwa projekty SpringBoot oraz projekt React
import subprocess

folders = ["hapi-fhir-jpaserver-starter-helm-v0.13.0", "dispatcherServer", "fhir_application"]

for folder in folders:
    try:
        subprocess.run(["cd", folder], check=True, shell=True)
        subprocess.run(["docker-compose", "up", "-d"], check=True)
    except subprocess.CalledProcessError as e:
        print(f"Błąd podczas uruchamiania w folderze {folder}: {e}")

        
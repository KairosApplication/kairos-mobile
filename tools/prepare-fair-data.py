"""Prepare fair records in the isolated local PostgreSQL and register real API stock movements.
No app fixtures, provider calls, remote databases, backdated operations or destructive resets.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import uuid
from datetime import datetime, timezone
import httpx
import psycopg2
import firebase_admin
from firebase_admin import auth

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.integration'
ORIGIN = 'preparacao-feira-2026-11-06'
ACCOUNTS = ['brenoleal@email.com', 'enzoanegue@gmail.com']
# Price and opening balances are preparation data, not claims about a real retailer.
PRODUCTS = [
 ('Mercearia','Arroz branco 1 kg','Camil','7.90',120,5),
 ('Mercearia','Feijão carioca 1 kg','Camil','8.90',100,4),
 ('Mercearia','Açúcar cristal 1 kg','União','4.90',100,3),
 ('Mercearia','Macarrão espaguete 500 g','Renata','5.50',90,2),
 ('Mercearia','Café torrado 500 g','Melitta','24.90',80,0),
 ('Mercearia','Óleo de soja 900 ml','Liza','7.50',100,16),
 ('Bebidas','Água mineral 500 ml','Crystal','2.50',200,8),
 ('Bebidas','Leite integral 1 L','Italac','5.90',120,0),
 ('Bebidas','Suco de uva 1 L','Del Valle','8.90',90,4),
 ('Limpeza e higiene','Detergente neutro 500 ml','Ypê','2.90',100,6),
 ('Limpeza e higiene','Sabonete 85 g','Dove','4.50',100,2),
 ('Limpeza e higiene','Papel higiênico 4 rolos','Neve','7.90',80,20),
]
ALERTS = {
 'Açúcar cristal 1 kg':'Açúcar com saldo baixo: conferir a gôndola e preparar a reposição.',
 'Macarrão espaguete 500 g':'Macarrão com poucas unidades: verificar a necessidade de reposição.',
 'Café torrado 500 g':'Café sem saldo na gôndola: priorizar a reposição.',
 'Leite integral 1 L':'Leite sem saldo na gôndola: conferir o estoque de origem.',
 'Suco de uva 1 L':'Suco com saldo baixo: conferir a disponibilidade na gôndola.',
 'Sabonete 85 g':'Sabonete com poucas unidades: preparar a reposição.',
}
MOVEMENTS = [
 [('Arroz branco 1 kg',20,1),('Feijão carioca 1 kg',15,0),('Água mineral 500 ml',24,0),('Detergente neutro 500 ml',12,1)],
 [('Arroz branco 1 kg',18,0),('Feijão carioca 1 kg',12,1),('Água mineral 500 ml',18,0),('Detergente neutro 500 ml',10,0)],
]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Write only to the fixed, isolated kairos_dev database')
    args = parser.parse_args()
    connection = psycopg2.connect(host='127.0.0.1',port=5433,dbname='kairos_dev',user='kairos_dev',password='kairos-local-only',application_name=ORIGIN)
    with connection.cursor() as cur:
        cur.execute('SELECT current_database()')
        assert cur.fetchone()[0]=='kairos_dev'
        cur.execute("SELECT id FROM companies WHERE email='company@kairos.local'")
        company, = cur.fetchone()
        employees=[]
        for email in ACCOUNTS:
            cur.execute('SELECT e.id,e.company_id,e.position FROM employees e JOIN users u ON u.id=e.users_id WHERE u.email=%s',(email,))
            employee, actual_company, role=cur.fetchone()
            assert actual_company==company and role=='STOCKER'
            employees.append(employee)
        cur.execute('SELECT (SELECT count(*) FROM products WHERE company_id=%s),(SELECT count(*) FROM alerts a JOIN shelves sh ON sh.id=a.shelf_id JOIN aisles ai ON ai.id=sh.aisle_id JOIN sectors s ON s.id=ai.sector_id WHERE s.company_id=%s),(SELECT count(*) FROM restockings r JOIN employees e ON e.id=r.employee_id WHERE e.company_id=%s)',(company,company,company))
        print('Antes: produtos, alertas, reposições =',cur.fetchone())
    connection.rollback()
    if not args.apply:
        print('Plano: 12 produtos, 3 setores/gôndolas, 6 alertas e 4 reposições por conta. Use --apply para executar.')
        return
    backup_dir=RUNTIME/'backups'
    backup_dir.mkdir(parents=True,exist_ok=True)
    backup=backup_dir/('before-fair-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.dump')
    with backup.open('wb') as output:
        subprocess.run([r'D:\KairosRuntime\DockerDesktop\resources\bin\docker.exe','--context','desktop-linux','compose','-f',str(RUNTIME/'kairos-springboot/compose.dev.yaml'),'exec','-T','postgres','pg_dump','-U','kairos_dev','-d','kairos_dev','-Fc'],stdout=output,check=True)
    assert backup.stat().st_size>0
    prepared={}
    with connection:
        with connection.cursor() as cur:
            cur.execute('UPDATE companies SET name=%s WHERE id=%s',('Mercado Kairos · Feira',company))
            for sector in dict.fromkeys(row[0] for row in PRODUCTS):
                cur.execute('INSERT INTO sectors(company_id,name,type) VALUES (%s,%s,%s) ON CONFLICT(company_id,name) DO UPDATE SET name=EXCLUDED.name RETURNING id',(company,sector,sector))
                sector_id,=cur.fetchone()
                cur.execute('INSERT INTO aisles(sector_id,position) VALUES (%s,1) ON CONFLICT(sector_id,position) DO UPDATE SET position=EXCLUDED.position RETURNING id',(sector_id,))
                aisle,=cur.fetchone()
                cur.execute('SELECT id FROM shelves WHERE aisle_id=%s ORDER BY id LIMIT 1',(aisle,))
                existing=cur.fetchone()
                if existing: shelf=existing[0]
                else:
                    cur.execute('INSERT INTO shelves(aisle_id,maximum_capacity) VALUES (%s,250) RETURNING id',(aisle,))
                    shelf,=cur.fetchone()
                cur.execute('SELECT id FROM inventories WHERE sector_id=%s AND company_id=%s ORDER BY id LIMIT 1',(sector_id,company))
                existing=cur.fetchone()
                if existing: inventory=existing[0]
                else:
                    cur.execute('INSERT INTO inventories(company_id,sector_id) VALUES (%s,%s) RETURNING id',(company,sector_id))
                    inventory,=cur.fetchone()
                cur.execute('INSERT INTO categories(company_id,category) VALUES (%s,%s) ON CONFLICT(company_id,category) DO UPDATE SET category=EXCLUDED.category RETURNING id',(company,sector))
                category,=cur.fetchone()
                for employee in employees:
                    cur.execute('INSERT INTO employee_aisles(employee_id,aisle_id) VALUES (%s,%s) ON CONFLICT(employee_id,aisle_id) DO NOTHING',(employee,aisle))
                    cur.execute('INSERT INTO inventory_employees(employee_id,inventory_id) VALUES (%s,%s) ON CONFLICT(inventory_id,employee_id) DO NOTHING',(employee,inventory))
                for _,name,brand,price,opening,visible in [p for p in PRODUCTS if p[0]==sector]:
                    cur.execute('SELECT id FROM products WHERE company_id=%s AND name=%s AND brand=%s ORDER BY id LIMIT 1',(company,name,brand))
                    existing=cur.fetchone()
                    if existing: product=existing[0]
                    else:
                        cur.execute('INSERT INTO products(company_id,category_id,name,brand,price) VALUES (%s,%s,%s,%s,%s::numeric::money) RETURNING id',(company,category,name,brand,price))
                        product,=cur.fetchone()
                    cur.execute('INSERT INTO product_inventories(inventory_id,product_id,product_quantity) VALUES (%s,%s,%s) ON CONFLICT(inventory_id,product_id) DO NOTHING',(inventory,product,opening))
                    if visible:
                        cur.execute('INSERT INTO product_shelves(product_id,shelf_id,product_quantity) VALUES (%s,%s,%s) ON CONFLICT(product_id,shelf_id) DO NOTHING',(product,shelf,visible))
                    prepared[name]={'productId':product,'shelfId':shelf,'inventoryId':inventory}
                    if name in ALERTS:
                        cur.execute('SELECT id FROM alerts WHERE shelf_id=%s AND product_id=%s AND description=%s',(shelf,product,ALERTS[name]))
                        if not cur.fetchone():
                            cur.execute("INSERT INTO alerts(stockout_date,shelf_id,product_id,description,status) VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date,%s,%s,%s,'OPEN')",(shelf,product,ALERTS[name]))
    credential=next((RUNTIME/'secrets').glob('*firebase*.json'))
    os.environ['GOOGLE_APPLICATION_CREDENTIALS']=str(credential)
    firebase_admin.initialize_app(options={'projectId':'germinimobile'})
    config=json.loads((ROOT/'app/google-services.json').read_text())
    api_key=config['client'][0]['api_key'][0]['current_key']
    manifest={'origin':ORIGIN,'backup':str(backup),'products':prepared,'accounts':[]}
    with httpx.Client(timeout=45,trust_env=False) as client:
        for index,email in enumerate(ACCOUNTS):
            user=auth.get_user_by_email(email)
            assert not user.disabled
            exchange=client.post('https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken',params={'key':api_key},json={'token':auth.create_custom_token(user.uid).decode(),'returnSecureToken':True})
            assert exchange.status_code==200, 'Firebase token exchange failed'
            headers={'Authorization':'Bearer '+exchange.json()['idToken'],'X-Kairos-Identity':'firebase'}
            base='http://127.0.0.1:8081/api/v1'
            session=client.get(base+'/mobile/session',headers=headers)
            assert session.status_code==200 and session.json()['companyId']==company
            ids=[]
            for name,quantity,damages in MOVEMENTS[index]:
                values=prepared[name]
                command=str(uuid.uuid5(uuid.NAMESPACE_URL,ORIGIN+':'+email+':'+name))
                result=client.post(base+'/mobile/restockings/registration',headers=headers,json={**values,'commandId':command,'restockedQuantity':quantity,'damagedQuantity':damages})
                assert result.status_code==201, f'Registration failed: HTTP {result.status_code}'
                ids.append(result.json()['id'])
            weekly=client.get(base+'/restockings/week?limit=500',headers=headers)
            alerts=client.get(base+'/mobile/alerts',headers=headers)
            summary=client.get(base+'/mobile/work-summary',headers=headers)
            assert weekly.status_code==alerts.status_code==summary.status_code==200
            assert all(record in {row['restocking_id'] for row in weekly.json()['items']} for record in ids)
            assert len(alerts.json())>=6 and summary.json()['restocks']>=4
            manifest['accounts'].append({'email':email,'restockingIds':ids,'weeklyTotal':weekly.json()['total'],'openAlerts':len(alerts.json()),'summary':summary.json()})
            print(email+': '+str(len(alerts.json()))+' alertas, '+str(weekly.json()['total'])+' reposições nesta semana; IDs '+str(ids))
    (RUNTIME/'fair-data-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    with connection.cursor() as cur:
        cur.execute('SELECT count(*) FROM product_inventories WHERE product_quantity<0')
        assert cur.fetchone()[0]==0
    print('Carga da feira persistida. Nenhum histórico foi retrodatado; API movimentou os saldos de estoque.')
    print('Backup local:',backup.name)

if __name__=='__main__':
    main()

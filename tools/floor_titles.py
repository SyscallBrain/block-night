#!/usr/bin/env python3
"""Títulos dos 600 andares (en e pt), 50 por bairro. Escritos à mão para este projeto.

Gera app/src/main/res/values/floor_titles.xml e values-pt/floor_titles.xml.
Uso: python3 tools/floor_titles.py
"""
import pathlib
from xml.sax.saxutils import escape

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# (en, pt) por bairro. Ordem = ordem dos andares do bairro (1 → 50).
DISTRICTS = [
    # 1 Alfama
    [("Stairway", "Escadinhas"), ("Lookout", "Miradouro"), ("Tile wall", "Azulejo"), ("Cobblestones", "Calçada"),
     ("Balcony", "Varanda"), ("Courtyard", "Pátio"), ("Washhouse", "Lavadouro"), ("Fountain", "Chafariz"),
     ("Tavern", "Taberna"), ("Shutter", "Postigo"), ("Alley", "Beco"), ("Laundry line", "Estendal"),
     ("Old church", "Igreja velha"), ("Cat steps", "Degraus do gato"), ("Arch", "Arco"), ("Wall ivy", "Hera no muro"),
     ("Lantern", "Lanterna"), ("Coal store", "Carvoaria"), ("Cistern", "Cisterna"), ("Bell tower", "Campanário"),
     ("Doorway", "Porta"), ("Rooftop", "Telhado"), ("Chimney", "Chaminé"), ("Market stall", "Banca"),
     ("Fishing net", "Rede"), ("Wall niche", "Nicho"), ("Ceramic bench", "Banco de cerâmica"), ("Gate", "Portão"),
     ("Stone well", "Poço"), ("Hallway", "Corredor"), ("Mosaic", "Mosaico"), ("Clock", "Relógio"),
     ("Coffee corner", "Esquina do café"), ("Garden step", "Degrau do jardim"), ("Lattice", "Gelosia"),
     ("Stone bridge", "Ponte de pedra"), ("Archway", "Passagem"), ("Terrace", "Terraço"), ("Woodpile", "Lenheiro"),
     ("Water tank", "Depósito"), ("Fado window", "Janela do fado"), ("Narrow lane", "Viela"),
     ("Hanging pots", "Vasos suspensos"), ("Sunset ledge", "Parapeito do pôr do sol"), ("Old bakery", "Padaria antiga"),
     ("Harbour view", "Vista do porto"), ("Crooked house", "Casa torta"), ("Last steps", "Últimos degraus"),
     ("Castle wall", "Muralha")],
    # 2 Mouraria
    [("Crossroads", "Encruzilhada"), ("Spice shop", "Mercearia"), ("Minaret of tiles", "Azulejos do bairro"),
     ("Street market", "Feira de rua"), ("Painted door", "Porta pintada"), ("Brick arch", "Arco de tijolo"),
     ("Night bazaar", "Bazar noturno"), ("Cafe chairs", "Cadeiras do café"), ("Tin roof", "Telhado de zinco"),
     ("Mural", "Mural"), ("Iron gate", "Grade de ferro"), ("Tea house", "Casa de chá"), ("Wooden stall", "Barraca"),
     ("Corner shop", "Loja da esquina"), ("Dusty square", "Largo empoeirado"), ("Loom room", "Sala do tear"),
     ("Shadow wall", "Muro de sombra"), ("Bread oven", "Forno de pão"), ("Wind alley", "Beco do vento"),
     ("Market scale", "Balança"), ("Old cinema", "Cinema antigo"), ("Rope bridge", "Ponte de corda"),
     ("Street lamp", "Candeeiro"), ("Carpet shop", "Loja de tapetes"), ("Tram rail", "Carril"),
     ("Lantern market", "Mercado de lanternas"), ("Flower box", "Floreira"), ("Stone trough", "Tanque de pedra"),
     ("Coffee grinder", "Moinho de café"), ("Dye vats", "Tinas de tinta"), ("Paper kites", "Papagaios"),
     ("Tile dealer", "Vendedor de azulejos"), ("Garden gate", "Portão do jardim"), ("Wind chime", "Sino de vento"),
     ("Brass plate", "Placa de latão"), ("Noodle bar", "Bar de noodles"), ("Stair landing", "Patamar"),
     ("Sewing room", "Atelier de costura"), ("Basil pots", "Vasos de manjericão"), ("Watch tower", "Torre de vigia"),
     ("Hidden court", "Pátio escondido"), ("Fig tree", "Figueira"), ("Bird cage", "Gaiola"),
     ("Old tavern", "Taberna velha"), ("Marble step", "Degrau de mármore"), ("Lace shop", "Loja de renda"),
     ("Tower block", "Bloco"), ("Last market", "Último mercado")],
    # 3 Ribeira
    [("Quay", "Cais"), ("Riverside", "Margem"), ("Crane", "Guindaste"), ("Warehouse", "Armazém"),
     ("Fish hall", "Lota"), ("Ferry", "Cacilheiro"), ("Dock gate", "Porta do cais"), ("Barrel stack", "Pilha de barris"),
     ("Pier", "Pontão"), ("Anchor", "Âncora"), ("Rope coil", "Rolo de corda"), ("Buoy", "Bóia"),
     ("Harbour wall", "Muro do porto"), ("Net loft", "Sótão das redes"), ("Salt store", "Armazém de sal"),
     ("Sailor's bar", "Bar dos marinheiros"), ("Weighbridge", "Balança de cais"), ("Tide mark", "Marca da maré"),
     ("Gull roost", "Poiso das gaivotas"), ("Ship chandler", "Armazém de navios"), ("Lighthouse", "Farol"),
     ("Oil drum", "Bidão"), ("Customs shed", "Alfândega"), ("Iron pier", "Pontão de ferro"), ("Wharf steps", "Escadas do cais"),
     ("Sail loft", "Loja de velas"), ("Cargo hatch", "Escotilha"), ("Fishmonger", "Peixaria"), ("Tram stop", "Paragem"),
     ("Riverbed", "Leito do rio"), ("Slipway", "Rampa"), ("Mooring post", "Poste de amarração"), ("Storm drain", "Escoadouro"),
     ("Barge", "Batelão"), ("Ropewalk", "Cordoaria"), ("Dry dock", "Doca seca"), ("Pulley", "Roldana"),
     ("Gangplank", "Prancha"), ("Customs stamp", "Carimbo"), ("Ice house", "Casa do gelo"), ("Lantern post", "Poste de luz"),
     ("Pilot house", "Casa do piloto"), ("Stone quay", "Cais de pedra"), ("Sunken hull", "Casco afundado"),
     ("Dock clock", "Relógio do cais"), ("Foghorn", "Sirene"), ("Harbour master", "Capitania"),
     ("Cove", "Enseada"), ("Last berth", "Último cais")],
    # 4 Miragaia
    [("Slope", "Ladeira"), ("Vine arbour", "Latada"), ("Stone gate", "Portal"), ("Old wall", "Muralha antiga"),
     ("Water spout", "Gárgula"), ("Olive press", "Lagar"), ("Chapel", "Capela"), ("Hidden garden", "Jardim escondido"),
     ("Long stairs", "Escadaria"), ("Crumbling arch", "Arco gasto"), ("Cistern door", "Porta da cisterna"),
     ("Old well", "Poço antigo"), ("Donkey path", "Caminho de burros"), ("Bell rope", "Corda do sino"),
     ("Terrace wall", "Muro de socalco"), ("Roof garden", "Jardim no telhado"), ("Lime kiln", "Forno de cal"),
     ("Shrine", "Oratório"), ("Potter's yard", "Oficina de oleiro"), ("Coach house", "Cavalariça"),
     ("Gatehouse", "Casa do guarda"), ("Stone trough", "Cocho"), ("Broken step", "Degrau partido"),
     ("Ivy tower", "Torre de hera"), ("Old press", "Prensa antiga"), ("Plum tree", "Ameixeira"), ("Iron railing", "Corrimão"),
     ("Wayside cross", "Cruzeiro"), ("Market garden", "Horta"), ("Yellow house", "Casa amarela"), ("Pottery kiln", "Forno de louça"),
     ("Tile roof", "Telhado de telha"), ("Thorn hedge", "Sebe"), ("Mossy wall", "Muro com musgo"), ("Gravel path", "Caminho de cascalho"),
     ("Wash basin", "Tanque"), ("Stone bench", "Banco de pedra"), ("Bread basket", "Cesto de pão"), ("Sundial", "Relógio de sol"),
     ("Spring", "Nascente"), ("Old orchard", "Pomar antigo"), ("Clay path", "Caminho de barro"), ("Hillside", "Encosta"),
     ("Mill race", "Levada"), ("Tall pines", "Pinheiros"), ("Lookout rail", "Varandim"), ("Watchman's hut", "Guarita"),
     ("Last ridge", "Cumeeira")],
    # 5 Bairro Alto
    [("Night club", "Clube noturno"), ("Neon sign", "Néon"), ("Fado house", "Casa de fado"), ("Bar counter", "Balcão"),
     ("Glass door", "Porta de vidro"), ("Dance floor", "Pista"), ("Late kitchen", "Cozinha de madrugada"), ("Vinyl shop", "Discos"),
     ("Pub sign", "Letreiro"), ("Brass rail", "Corrimão de latão"), ("Velvet booth", "Reservado"), ("Stage light", "Foco"),
     ("Ticket booth", "Bilheteira"), ("Cellar stairs", "Escadas da cave"), ("Street piano", "Piano de rua"), ("Cocktail bar", "Bar de cocktails"),
     ("Graffiti wall", "Parede de graffiti"), ("Bike rack", "Estacionamento de bicicletas"), ("Taxi rank", "Praça de táxis"),
     ("Corner café", "Café de esquina"), ("Jukebox", "Jukebox"), ("Rooftop bar", "Bar no telhado"), ("Poster wall", "Cartazes"),
     ("Back alley", "Traseiras"), ("Lift shaft", "Caixa do elevador"), ("Late tram", "Elétrico da noite"), ("Smoke room", "Sala de fumo"),
     ("Gallery", "Galeria"), ("Bookshop", "Livraria"), ("Opera box", "Camarote"), ("Coat check", "Guarda-roupa"),
     ("Doorman", "Porteiro"), ("Wine cellar", "Adega"), ("Old theatre", "Teatro antigo"), ("Sweet shop", "Pastelaria"),
     ("Night bus", "Autocarro da noite"), ("Mirror hall", "Sala de espelhos"), ("Tin bucket", "Balde"), ("Balcony crowd", "Varanda cheia"),
     ("Stage door", "Porta de artistas"), ("Paper lantern", "Lanterna de papel"), ("Music school", "Escola de música"),
     ("Fire escape", "Escada de incêndio"), ("Last round", "Última rodada"), ("Dawn steps", "Escadas do amanhecer"),
     ("Hidden bar", "Bar escondido"), ("Chandelier", "Candeeiro de cristal")],
    # 6 Chiado
    [("Shop window", "Montra"), ("Bookbinder", "Encadernador"), ("Glass arcade", "Arcada"), ("Silver shop", "Ourivesaria"),
     ("Hat maker", "Chapelaria"), ("Gilded frame", "Moldura dourada"), ("Theatre front", "Fachada do teatro"), ("Marble counter", "Balcão de mármore"),
     ("Tea room", "Salão de chá"), ("Cobbled square", "Largo"), ("Bronze statue", "Estátua de bronze"), ("Fine stationery", "Papelaria"),
     ("Music score", "Partitura"), ("Perfume shop", "Perfumaria"), ("Clock tower", "Torre do relógio"), ("Gold leaf", "Folha de ouro"),
     ("Wrought gate", "Gradeamento"), ("Upper floor", "Andar de cima"), ("Ceiling stucco", "Estuque"), ("Glove shop", "Luvaria"),
     ("Old bookshop", "Livraria antiga"), ("Ink well", "Tinteiro"), ("Typewriter", "Máquina de escrever"), ("Velvet curtain", "Cortina de veludo"),
     ("Chandelier hall", "Sala dos candeeiros"), ("Confectionery", "Confeitaria"), ("Tile floor", "Pavimento de azulejo"), ("Parisian arch", "Arco"),
     ("Stone mason", "Cantaria"), ("Church steps", "Escadas da igreja"), ("Photo studio", "Estúdio fotográfico"), ("Brass plaque", "Placa de bronze"),
     ("Tailor", "Alfaiate"), ("Piano shop", "Loja de pianos"), ("Opera poster", "Cartaz da ópera"), ("Wood panel", "Painel de madeira"),
     ("Coffee house", "Casa de café"), ("Lamp post", "Candeeiro de rua"), ("Balcony rail", "Varanda de ferro"), ("Shop bell", "Campainha"),
     ("Antique clock", "Relógio antigo"), ("Fountain square", "Praça da fonte"), ("Shopping passage", "Passagem"), ("Mirror shop", "Espelhos"),
     ("Quiet courtyard", "Pátio silencioso"), ("Gilt sign", "Letreiro dourado"), ("Last window", "Última montra")],
    # 7 Baixa
    [("Grid street", "Rua em quadrícula"), ("Arcade column", "Coluna"), ("Stone facade", "Fachada"), ("Pombal square", "Praça do Marquês"),
     ("Triumphal arch", "Arco triunfal"), ("Cast-iron elevator", "Elevador"), ("Ground floor shop", "Loja do rés-do-chão"), ("Crosswalk", "Passadeira"),
     ("Granite curb", "Lancil"), ("Tram line", "Linha de elétrico"), ("Clock face", "Mostrador"), ("Office block", "Prédio de escritórios"),
     ("Staircase hall", "Hall de escadas"), ("Tiled pavement", "Calçada portuguesa"), ("Bank vault", "Cofre"), ("Newspaper kiosk", "Quiosque"),
     ("Loading bay", "Cais de carga"), ("Stone lion", "Leão de pedra"), ("Basement archive", "Arquivo"), ("Ledger room", "Sala dos livros"),
     ("Shop sign", "Tabuleta"), ("Brass handle", "Puxador de latão"), ("Lift cage", "Cabine"), ("Dress shop", "Loja de vestidos"),
     ("Tobacco kiosk", "Tabacaria"), ("Glass canopy", "Toldo de vidro"), ("Fire door", "Porta corta-fogo"), ("Ticket hall", "Bilheteira"),
     ("Bus shelter", "Abrigo"), ("Water main", "Cano"), ("Sewer grate", "Grelha"), ("Coffee roaster", "Torrefação"),
     ("Bakery vent", "Chaminé da padaria"), ("Cinema screen", "Ecrã"), ("Fountain basin", "Tanque da fonte"), ("Lamp row", "Fila de candeeiros"),
     ("Parking deck", "Garagem"), ("Hotel lobby", "Lobby"), ("Telephone box", "Cabine telefónica"), ("Stone plinth", "Pedestal"),
     ("Wide avenue", "Avenida"), ("Post office", "Correio"), ("Market hall", "Mercado"), ("Office roof", "Terraço de escritórios"),
     ("Plaza steps", "Escadaria da praça"), ("Cross street", "Travessa"), ("Glass tower", "Torre de vidro"), ("Last corner", "Última esquina")],
    # 8 Bonfim
    [("Brick block", "Bloco de tijolo"), ("Tenement stairs", "Escadas do prédio"), ("Workers' vent", "Respiradouro"), ("Factory gate", "Portão da fábrica"),
     ("Boiler house", "Caldeira"), ("Chimney stack", "Chaminé"), ("Loom shed", "Barracão"), ("Cement yard", "Pátio de cimento"),
     ("Railway cutting", "Trincheira"), ("Freight shed", "Armazém de carga"), ("Steel beam", "Viga de aço"), ("Coal bunker", "Silo de carvão"),
     ("Water tower", "Depósito de água"), ("Pipe rack", "Estrutura de tubos"), ("Rail siding", "Desvio"), ("Gasworks", "Gasómetro"),
     ("Canal lock", "Eclusa"), ("Mill wheel", "Roda de moinho"), ("Bottle store", "Armazém de garrafas"), ("Brick kiln", "Forno de tijolo"),
     ("Machine hall", "Sala das máquinas"), ("Crane cab", "Cabina do guindaste"), ("Rivet line", "Linha de rebites"), ("Staff canteen", "Refeitório"),
     ("Time clock", "Relógio de ponto"), ("Locker room", "Balneário"), ("Yard fence", "Vedação"), ("Oil drum stack", "Pilha de bidões"),
     ("Fire hydrant", "Hidrante"), ("Signal box", "Cabine de sinalização"), ("Loading dock", "Cais"), ("Pressure valve", "Válvula"),
     ("Ventilation shaft", "Poço de ventilação"), ("Iron staircase", "Escada de ferro"), ("Tin roof shed", "Telhado de zinco"), ("Overhead line", "Catenária"),
     ("Glass skylight", "Clarabóia"), ("Water trough", "Bebedouro"), ("Boiler gauge", "Manómetro"), ("Corrugated wall", "Parede ondulada"),
     ("Bridge span", "Vão"), ("Cable drum", "Bobina"), ("Conveyor belt", "Tapete"), ("Warehouse roof", "Cobertura"),
     ("Night shift", "Turno da noite"), ("Dust cloud", "Pó"), ("Lamp cage", "Gaiola de lâmpada"), ("Last shift", "Último turno")],
    # 9 Foz
    [("Ocean wall", "Muro do mar"), ("Surf break", "Ondas"), ("Lighthouse ridge", "Cabo"), ("Promenade", "Passeio"),
     ("Sea shelter", "Abrigo"), ("Beach bar", "Bar de praia"), ("Tide pool", "Poça"), ("Rocky shore", "Costa rochosa"),
     ("Sand dune", "Duna"), ("Wooden pier", "Pontão de madeira"), ("Sea wind", "Vento"), ("Kite field", "Campo de papagaios"),
     ("Sunset deck", "Deque"), ("Salt spray", "Maresia"), ("Gull perch", "Poleiro"), ("Fishing hut", "Palheiro"),
     ("Seaside villa", "Vivenda"), ("Cliff path", "Trilho"), ("Surf shop", "Loja de surf"), ("Tiled bench", "Banco de azulejo"),
     ("Breakwater", "Quebra-mar"), ("Old fort", "Forte"), ("Coral tile", "Azulejo de coral"), ("Ferry ramp", "Rampa"),
     ("Ice cream stand", "Gelados"), ("Bike lane", "Ciclovia"), ("Dune fence", "Vedação da duna"), ("Seaweed heap", "Algas"),
     ("Tide gauge", "Maregrafo"), ("Shell path", "Caminho de conchas"), ("Harbour mouth", "Barra"), ("Fishing net drying", "Redes a secar"),
     ("Sea chapel", "Capela do mar"), ("Rain shelter", "Alpendre"), ("Driftwood", "Madeira"), ("Blue tiles", "Azulejos azuis"),
     ("Pine grove", "Pinhal"), ("Bay window", "Janela de mar"), ("Foam line", "Linha de espuma"), ("Yacht club", "Clube náutico"),
     ("Sea cave", "Gruta"), ("Stone steps down", "Escadas para o mar"), ("Rip current", "Corrente"), ("Sun deck", "Esplanada"),
     ("Moon tide", "Maré-viva"), ("Pier light", "Luz do pontão"), ("Last wave", "Última onda")],
    # 10 Belém
    [("Monastery cloister", "Claustro"), ("Stone carving", "Talha de pedra"), ("Rope column", "Coluna de corda"), ("Navigator statue", "Padrão"),
     ("Tower balcony", "Varanda da torre"), ("Tile panel", "Painel de azulejos"), ("Explorer ship", "Caravela"), ("Dome", "Cúpula"),
     ("Cloister arch", "Arcada"), ("Gothic window", "Janela gótica"), ("Chapel nave", "Nave"), ("Tomb", "Túmulo"),
     ("Sail motif", "Vela"), ("Bronze globe", "Globo"), ("Garden hedge", "Sebe"), ("Palace gate", "Portão do palácio"),
     ("Coach museum", "Museu dos coches"), ("Carriage hall", "Sala dos coches"), ("River steps", "Escadaria do rio"), ("Cannon", "Canhão"),
     ("Fort wall", "Muralha do forte"), ("Lighthouse tower", "Torre"), ("Cork oak", "Sobreiro"), ("Pastry queue", "Fila"),
     ("Stone cross", "Cruz de pedra"), ("Marble floor", "Chão de mármore"), ("Painted ceiling", "Teto pintado"), ("Choir loft", "Coro"),
     ("Organ pipes", "Tubos do órgão"), ("Crypt", "Cripta"), ("Ornate doorway", "Portal"), ("Sculpted rope", "Corda esculpida"),
     ("Water garden", "Jardim de água"), ("Reflecting pool", "Espelho de água"), ("Gate tower", "Torreão"), ("Watch bell", "Sino"),
     ("Palm avenue", "Alameda"), ("Tram loop", "Volta do elétrico"), ("Museum yard", "Pátio do museu"), ("Pillar base", "Base de pilar"),
     ("Relief frieze", "Friso"), ("Vaulted room", "Sala abobadada"), ("Iron bridge", "Ponte de ferro"), ("Sunset wall", "Muro do sol"),
     ("Nautical chart", "Carta náutica"), ("Last cloister", "Último claustro")],
    # 11 Avenidas Novas
    [("Boulevard", "Boulevard"), ("Glass office", "Escritório de vidro"), ("Concrete plaza", "Praça de betão"), ("Bus lane", "Faixa"),
     ("Underground stop", "Estação"), ("Parking tower", "Torre de estacionamento"), ("Hotel atrium", "Átrio"), ("Garden square", "Jardim"),
     ("Tennis court", "Ténis"), ("Shopping mall", "Centro comercial"), ("Terrace café", "Esplanada"), ("Tower crane", "Grua"),
     ("Sky lobby", "Lobby do céu"), ("Solar panel", "Painéis solares"), ("Bike share", "Bicicletas partilhadas"), ("Electric bus", "Autocarro elétrico"),
     ("Office skybridge", "Passadiço"), ("Lab window", "Janela do laboratório"), ("Data hall", "Sala de servidores"), ("Gym floor", "Ginásio"),
     ("Rooftop garden", "Jardim no topo"), ("Fountain plaza", "Praça com fonte"), ("Street art wall", "Mural urbano"), ("Coworking desk", "Secretária"),
     ("Glass lift", "Elevador de vidro"), ("Metro vent", "Grelha do metro"), ("Traffic island", "Placa central"), ("Stadium gate", "Portão do estádio"),
     ("Parking deck 4", "Piso 4"), ("Pedestrian bridge", "Passagem pedonal"), ("Modern church", "Igreja moderna"), ("Cinema plaza", "Praça do cinema"),
     ("Conference room", "Sala de reuniões"), ("Startup loft", "Loft"), ("Corporate lobby", "Receção"), ("Park bench", "Banco de jardim"),
     ("Night glow", "Brilho"), ("Service road", "Estrada de serviço"), ("Lighting rig", "Projetores"), ("Pop-up stall", "Stand"),
     ("Museum wing", "Ala do museu"), ("Business avenue", "Avenida"), ("Crosswalk signal", "Semáforo"), ("Office elevator", "Elevador"),
     ("Last boulevard", "Último boulevard")],
    # 12 Parque das Nações
    [("Glass tower", "Torre de vidro"), ("Waterfront", "Frente de água"), ("Cable car", "Teleférico"), ("Marina", "Marina"),
     ("Oceanarium", "Oceanário"), ("Footbridge", "Passadeira"), ("Wave roof", "Cobertura em onda"), ("Aquarium glass", "Vidro do aquário"),
     ("Expo hall", "Pavilhão"), ("Sail canopy", "Toldo"), ("Light column", "Coluna de luz"), ("Floating deck", "Deque flutuante"),
     ("Steel arch", "Arco de aço"), ("Mirror façade", "Fachada espelhada"), ("Water mirror", "Espelho d'água"), ("Electric shuttle", "Shuttle"),
     ("Solar tower", "Torre solar"), ("Green wall", "Parede verde"), ("Harbour edge", "Margem"), ("Skyline", "Skyline"),
     ("Glass lift shaft", "Caixa do elevador"), ("Concourse", "Átrio"), ("Tidal gate", "Comporta"), ("Pier cafe", "Café do pontão"),
     ("Sky bridge", "Ponte aérea"), ("Glass floor", "Chão de vidro"), ("Atrium garden", "Jardim interior"), ("Crane line", "Linha de gruas"),
     ("Wind turbine", "Turbina"), ("Concert hall", "Sala de concertos"), ("Ticket gate", "Torniquete"), ("Bike path", "Ciclovia"),
     ("Tide display", "Painel da maré"), ("Dock office", "Escritório do cais"), ("Glass cube", "Cubo de vidro"), ("Hotel tower", "Torre hoteleira"),
     ("Promenade rail", "Corrimão"), ("Open square", "Praça aberta"), ("Light show", "Espetáculo de luz"), ("Water jet", "Jato de água"),
     ("Cable pylon", "Pilão"), ("Observation deck", "Miradouro"), ("Riverside steps", "Escadas da margem"), ("Last pier", "Último pontão")],
]

# Completa cada bairro até 50 títulos (as listas acima tinham entre 44 e 49).
EXTRA = {
    1: [("Last lantern", "Lanterna da noite")],
    2: [("Spice alley", "Beco das especiarias"), ("Pigeon roof", "Telhado dos pombos")],
    3: [("Customs queue", "Fila da alfândega")],
    4: [("Wine store", "Adega"), ("Goat track", "Carreiro")],
    5: [("Late bar", "Bar de madrugada"), ("Piano bar", "Bar do piano"), ("Velvet rope", "Corda de veludo")],
    6: [("Lift grille", "Grade"), ("Wax museum", "Museu de cera"), ("Golden door", "Porta dourada")],
    7: [("Subway mouth", "Entrada do metro"), ("Clock stair", "Escadas do relógio")],
    8: [("Dye works", "Tinturaria"), ("Canal bridge", "Ponte do canal")],
    9: [("Cove steps", "Degraus da enseada"), ("Last sunset", "Último pôr do sol"), ("Dune path", "Trilho da duna")],
    10: [("Cannon gate", "Porta do forte"), ("Chapel bell", "Sino da capela"), ("Pastry bench", "Banco"), ("Spice ship", "Nau")],
    11: [("Cafe terrace", "Terraço do café"), ("Metro hall", "Átrio do metro"), ("Plaza light", "Luz da praça"), ("Sign board", "Painel"), ("Tram loop 11", "Volta de elétrico")],
    12: [("Pier 12", "Pontão 12"), ("Ferry hall", "Sala do cacilheiro"), ("Sail mast", "Mastro"), ("Lantern row", "Fila de luzes"), ("Glass bridge", "Ponte de vidro"), ("Quiet deck", "Deque silencioso")],
}
for _i, _extra in EXTRA.items():
    DISTRICTS[_i - 1].extend(_extra)

def build(lang_index: int) -> str:
    items = []
    for district in DISTRICTS:
        assert len(district) == 50, len(district)
        items.extend(pair[lang_index] for pair in district)
    assert len(items) == 600
    body = "\n".join(f'        <item>{escape(t).replace("'", "\\'")}</item>' for t in items)
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string-array name="floor_titles">\n{body}\n    </string-array>\n</resources>\n'

if __name__ == "__main__":
    (RES / "values").mkdir(parents=True, exist_ok=True)
    (RES / "values-pt").mkdir(parents=True, exist_ok=True)
    (RES / "values/floor_titles.xml").write_text(build(0))
    (RES / "values-pt/floor_titles.xml").write_text(build(1))
    print("escritos 600 títulos em en e pt")

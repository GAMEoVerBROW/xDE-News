package com.meunews.reader

data class Src(val name: String, val url: String, val pt: Boolean, val desc: String = "", val icon: String? = null,
               val followers: Long = 0, val lang: String = "", val site: String? = null)
data class Sub(val name: String, val items: List<Src>)
data class Cat(val name: String, val subs: List<Sub>)
data class Variant(val label: String, val feed: Feed)

private fun p(n: String, u: String, d: String = "") = Src(n, u, true, desc = d)
private fun e(n: String, u: String, d: String = "") = Src(n, u, false, desc = d)
private fun sub(name: String, vararg i: Src) = Sub(name, i.toList())
private fun cat(name: String, vararg s: Sub) = Cat(name, s.toList())
private fun one(name: String, vararg i: Src) = Cat(name, listOf(Sub("Tudo", i.toList())))

val STYLE = mapOf(
    "Notícias Brasil" to Pair(0xFF1B5E20, "🇧🇷"), 
    "Notícias Mundo" to Pair(0xFF0D47A1, "🌍"),
    "Política" to Pair(0xFF455A64, "🏛️"), 
    "Tecnologia" to Pair(0xFF4527A0, "💻"),
    "Inteligência Artificial" to Pair(0xFF512DA8, "🤖"), 
    "Games & eSports" to Pair(0xFF880E4F, "🎮"),
    "Esportes" to Pair(0xFFE65100, "⚽"), 
    "Cinema, Séries & Geek" to Pair(0xFFAD1457, "🎬"),
    "Negócios & Economia" to Pair(0xFF006064, "📈"), 
    "Cripto & Web3" to Pair(0xFFB26A00, "₿"),
    "Ciência & Espaço" to Pair(0xFF283593, "🔬"),
    "Música & Cultura" to Pair(0xFF6A1B9A, "🎵"),
    "Carros & Mobilidade" to Pair(0xFFBF360C, "🚗"),
    "Programação & Dev" to Pair(0xFF1565C0, "👨‍💻"), 
    "Saúde & Bem-Estar" to Pair(0xFF00695C, "🩺")
)

val CATALOG = listOf(
    // -------------------------------------------------------------
    // 1. NOTÍCIAS BRASIL
    // -------------------------------------------------------------
    one("Notícias Brasil",
        p("G1", "https://g1.globo.com/rss/g1/", "Principal portal de notícias do Brasil da Rede Globo."),
        p("Folha de S.Paulo", "https://feeds.folha.uol.com.br/emcimadahora/rss091.xml", "Jornalismo de credibilidade e cobertura política profunda."),
        p("UOL", "http://rss.uol.com.br/feed/noticias.xml", "Notícias em tempo real, entretenimento e opinião."),
        p("CNN Brasil", "https://www.cnnbrasil.com.br/feed/", "Cobertura nacional e internacional de fatos em tempo real."),
        p("Metrópoles", "https://www.metropoles.com/feed", "Portal de notícias focado no Distrito Federal e cenário nacional."),
        p("Agência Brasil", "https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml", "Agência pública de notícias oficial do governo brasileiro."),
        p("BBC News Brasil", "https://feeds.bbci.co.uk/portuguese/rss.xml", "Jornalismo internacional com foco na realidade brasileira."),
        p("DW Brasil", "https://rss.dw.com/rdf/rss-br-all", "Perspectiva alemã e europeia sobre o Brasil e o mundo."),
        p("O Globo", "https://oglobo.globo.com/rss.xml", "Tradição e cobertura aprofundada do Rio e do Brasil."),
        p("Veja", "https://veja.abril.com.br/feed/", "Revista semanal de notícias, política e análises exclusivas."),
        p("Jovem Pan", "https://jovempan.com.br/feed", "Notícias, rádio, política e debates nacionais."),
        p("CartaCapital", "https://www.cartacapital.com.br/feed/", "Análise crítica, política e economia sob ótica progressista."),
        p("Poder360", "https://www.poder360.com.br/feed/", "Jornalismo digital focado em poder e política institucional.")
    ),

    // -------------------------------------------------------------
    // 2. NOTÍCIAS MUNDO (POR PAÍSES / REGIÕES)
    // -------------------------------------------------------------
    cat("Notícias Mundo",
        sub("América do Norte", 
            e("The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "Referência mundial em jornalismo investigativo (EUA)."),
            e("The Washington Post", "https://feeds.washingtonpost.com/rss/world", "Cobertura aprofundada de política dos EUA e geopolítica."),
            e("CNN International", "http://rss.cnn.com/rss/edition.rss", "Rede global de notícias em tempo real."),
            e("The Wall Street Journal", "https://feeds.a.dj.com/rss/RSSWorldNews.xml", "Economia, finanças e notícias globais de impacto."),
            e("CBC News (Canadá)", "https://www.cbc.ca/webfeed/rss/rss-world", "Principais notícias e cobertura internacional do Canadá.")
        ),
        sub("Europa", 
            e("BBC News (Reino Unido)", "https://feeds.bbci.co.uk/news/world/rss.xml", "Uma das maiores e mais confiáveis redes de notícias do mundo."),
            e("The Guardian (Reino Unido)", "https://www.theguardian.com/world/rss", "Jornalismo independente, investigativo e global."),
            e("France 24 (França)", "https://www.france24.com/en/rss", "Visão europeia e francesa sobre os conflitos e acordos globais."),
            e("DW English (Alemanha)", "https://rss.dw.com/rdf/rss-en-all", "Notícias da Alemanha e da União Europeia em inglês."),
            e("Euronews", "https://www.euronews.com/rss?format=mrss", "O pulso político e econômico da União Europeia."),
            e("El País (Espanha)", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "Principal jornal generalista em língua espanhola.")
        ),
        sub("Ásia & Oriente Médio", 
            e("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "Perspectiva aprofundada do Oriente Médio e do Sul Global."),
            e("South China Morning Post (Hong Kong)", "https://www.scmp.com/rss/91/feed", "Notícias da China, Ásia e comércio internacional."),
            e("The Japan Times", "https://www.japantimes.co.jp/feed/", "O jornal em inglês mais tradicional do Japão."),
            e("Times of India", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "Acontecimento e atualidades do subcontinente indiano.")
        ),
        sub("América Latina", 
            e("Clarín (Argentina)", "https://www.clarin.com/rss/lo-ultimo/", "O principal jornal diário da Argentina."),
            e("El Universal (México)", "https://www.eluniversal.com.mx/rss.xml", "Notícias de destaque do México e América Latina."),
            e("El Comercio (Peru)", "https://elcomercio.pe/arc/outboundfeeds/rss/?outputType=xml", "Tradição informativa no Peru.")
        )
    ),

    // -------------------------------------------------------------
    // 3. POLÍTICA
    // -------------------------------------------------------------
    cat("Política",
        sub("Brasil", 
            p("Poder360", "https://www.poder360.com.br/feed/", "Notícias dos bastidores de Brasília e dos três poderes."),
            p("CartaCapital", "https://www.cartacapital.com.br/feed/", "Debates, entrevistas e análises políticas e econômicas."),
            p("Metrópoles Política", "https://www.metropoles.com/colunas/igor-gadelha/feed", "Furos e colunas exclusivas do cenário político nacional."),
            p("Nexo Jornal", "https://www.nexojornal.com.br/rss.xml", "Jornalismo explicativo de contexto das políticas públicas."),
            p("The Intercept Brasil", "https://www.intercept.com.br/feed/", "Jornalismo investigativo focado na apuração do poder público.")
        ),
        sub("Mundo", 
            e("Politico", "https://www.politico.com/rss/politicopicks.xml", "O dia a dia do poder executivo e legislativo nos EUA e Europa."),
            e("The Hill", "https://thehill.com/feed/", "Informaçoes e movimentações do Congresso americano."),
            e("Foreign Policy", "https://foreignpolicy.com/feed/", "Relações internacionais, diplomacia e geopolítica mundial.")
        )
    ),

    // -------------------------------------------------------------
    // 4. ESPORTES (Com ESPN, GE, TNT, etc.)
    // -------------------------------------------------------------
    cat("Esportes",
        sub("Futebol & Geral (Brasil)", 
            p("ESPN Brasil", "https://www.espn.com.br/espn/rss/news", "Canais de esportes, ligas internacionais e análises técnicas."),
            p("Globo Esporte (GE)", "https://ge.globo.com/rss/", "O maior portal de futebol e esportes olímpicos do Brasil."),
            p("TNT Sports Brasil", "https://tntsports.com.br/rss.xml", "Champions League, futebol europeu e bastidores do esporte."),
            p("UOL Esporte", "http://rss.uol.com.br/feed/esporte.xml", "Análises, colunistas, mercado da bola e bastidores."),
            p("Trivela", "https://trivela.com.br/feed/", "Jornalismo esportivo focado em tática, história e futebol internacional."),
            p("Gazeta Esportiva", "https://www.gazetaesportiva.com/feed/", "Tradição centenária de cobertura de clubes brasileiros.")
        ),
        sub("Fórmula 1 & Automobilismo", 
            p("Grande Prêmio", "https://www.grandepremio.com.br/feed/", "A maior cobertura de F1, WEC e automobilismo do Brasil."),
            p("Motorsport.com Brasil", "https://br.motorsport.com/rss/f1/news/", "Bastidores, telemetria e notícias da Fórmula 1 em tempo real."),
            e("Formula1.com", "https://www.formula1.com/content/fom-website/en/latest/all.xml", "Portal oficial com comunicados da FIA e equipes.")
        ),
        sub("Ligas Internacionais & Lutas", 
            e("ESPN Soccer", "https://www.espn.com/espn/rss/soccer/news", "Cobertura global das maiores ligas europeias e copas."),
            e("ESPN NBA", "https://www.espn.com/espn/rss/nba/news", "Tudo sobre basquete norte-americano e superstars."),
            e("ESPN NFL", "https://www.espn.com/espn/rss/nfl/news", "Futebol americano, análises de draft e Super Bowl."),
            e("MMA Fighting", "https://www.mmafighting.com/rss/current", "Notícias especializadas de MMA, UFC e lutas pelo mundo.")
        )
    ),

    // -------------------------------------------------------------
    // 5. CINEMA, SÉRIES & GEEK (Com Ei Nerd, Omelete, etc.)
    // -------------------------------------------------------------
    cat("Cinema, Séries & Geek",
        sub("Brasil", 
            p("Ei Nerd", "https://www.einerd.com.br/feed/", "Cultura pop, heróis, animes, teorias e grandes lançamentos de bilheteria."),
            p("Omelete", "https://www.omelete.com.br/rss.xml", "O maior portal brasileiro de cultura pop, cinema, HQs e CCXP."),
            p("Jovem Nerd", "https://jovemnerd.com.br/feed/", "Notícias, nerdcast, filmes, séries e entretenimento lendário."),
            p("AdoroCinema", "https://www.adorocinema.com/rss/noticias.xml", "Guia completo de estreias de filmes, críticas e calendários de streaming."),
            p("Papelpop", "https://www.papelpop.com/feed/", "Música pop, séries de TV, cultura pop e celebridades.")
        ),
        sub("Mundo", 
            e("Variety", "https://variety.com/feed/", "A revista oficial da indústria de Hollywood e do entretenimento global."),
            e("The Hollywood Reporter", "https://www.hollywoodreporter.com/feed/", "Negócios do cinema, bastidores de estúdios e premiações."),
            e("Collider", "https://collider.com/feed/", "Críticas de filmes, entrevistas com diretores e exclusivas de streaming."),
            e("Deadline", "https://deadline.com/feed/", "Furos de reportagem sobre escalações de elencos e novos projetos de TV.")
        )
    ),

    // -------------------------------------------------------------
    // 6. GAMES & ESPORTS (Com Flow Games, IGN, etc.)
    // -------------------------------------------------------------
    cat("Games & eSports",
        sub("Brasil", 
            p("Flow Games", "https://flowgames.gg/feed/", "Portal de notícias, podcasts, reviews e cobertura de games do grupo Flow."),
            p("IGN Brasil", "https://br.ign.com/feed.xml", "Análises oficiais, notícias de lançamentos e cultura gamer no Brasil."),
            p("The Enemy", "https://www.theenemy.com.br/feed", "Jornalismo de games, eSports e cultura pop com profundidade."),
            p("Voxel", "https://www.voxel.com.br/feed", "Tudo sobre consoles, PC gaming, promoções e novidades."),
            p("MeuPlayStation", "https://meuplaystation.com.br/feed/", "Foco absoluto no ecossistema PlayStation e notícias da Sony."),
            p("Eurogamer Portugal", "https://www.eurogamer.pt/?format=rss", "Análises e jornalismo de games em português europeu.")
        ),
        sub("Mundo & eSports", 
            e("IGN (Global)", "https://www.ign.com/rss/v2/articles/feed", "A maior autoridade mundial em avaliações de jogos."),
            e("GameSpot", "https://www.gamespot.com/feeds/mashup/", "Reviews, trailers e coberturas completas de feiras globais."),
            e("Kotaku", "https://kotaku.com/rss", "Bastidores da indústria de games, cultura e opiniões cáusticas."),
            e("Polygon", "https://www.polygon.com/rss/index.xml", "Cultura de jogos, narrativas e análises artísticas."),
            e("PC Gamer", "https://www.pcgamer.com/rss/", "A Bíblia dos jogadores de computador e hardware dedicado."),
            e("Dexerto", "https://www.dexerto.com/feed/", "Cobertura veloz de eSports, streamers, criadores de conteúdo e games competitivos.")
        )
    ),

    // -------------------------------------------------------------
    // 7. TECNOLOGIA
    // -------------------------------------------------------------
    cat("Tecnologia",
        sub("Brasil", 
            p("Tecnoblog", "https://tecnoblog.net/feed/", "Notícias, análises de gadgets e operadoras."),
            p("Canaltech", "https://canaltech.com.br/rss/", "Tecnologia, análises, tutoriais e mercado mobile."),
            p("TecMundo", "https://rss.tecmundo.com.br/feed", "O ecossistema geek, hardware, segurança e tech."),
            p("Olhar Digital", "https://olhardigital.com.br/feed/", "Inovação, ciência, internet e mercado digital."),
            p("Showmetech", "https://www.showmetech.com.br/feed/", "Reviews de produtos e guias de tecnologia explicados."),
            p("Adrenaline", "https://adrenaline.com.br/feed/", "Foco em hardware de PC, placas de vídeo e games.")
        ),
        sub("Mundo", 
            e("The Verge", "https://www.theverge.com/rss/index.xml", "A interseção entre tecnologia, ciência, arte e cultura."),
            e("TechCrunch", "https://techcrunch.com/feed/", "Startups, venture capital, inovações e Vale do Silício."),
            e("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Jornalismo técnico aprofundado para geeks e cientistas."),
            e("Wired", "https://www.wired.com/feed/rss", "Como a tecnologia está mudando o futuro da cultura e do planeta."),
            e("Engadget", "https://www.engadget.com/rss.xml", "Lançamentos de eletrônicos de consumo globais.")
        )
    ),

    // -------------------------------------------------------------
    // 8. INTELIGÊNCIA ARTIFICIAL
    // -------------------------------------------------------------
    one("Inteligência Artificial",
        e("OpenAI News", "https://openai.com/news/rss.xml", "Atualizações oficiais dos criadores do ChatGPT."),
        e("Google AI Blog", "https://blog.google/technology/ai/rss/", "Avanços de pesquisa em IA do Google DeepMind."),
        e("Hugging Face Blog", "https://huggingface.co/blog/feed.xml", "Comunidade open-source de modelos de IA e machine learning."),
        e("MIT Tech Review · IA", "https://www.technologyreview.com/topic/artificial-intelligence/feed", "Análises críticas sobre o impacto real da inteligência artificial."),
        e("VentureBeat · IA", "https://venturebeat.com/category/ai/feed/", "O ecossistema empresarial e comercial de adoção de IA."),
        e("The Verge · IA", "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml", "Novidades do mercado de inteligências artificiais generativas.")
    ),

    // -------------------------------------------------------------
    // 9. NEGÓCIOS & CRIPTO
    // -------------------------------------------------------------
    cat("Negócios & Economia",
        sub("Brasil", 
            p("InfoMoney", "https://www.infomoney.com.br/feed/", "Mercado financeiro, bolsas, investimentos e economia."),
            p("Exame", "https://exame.com/feed/", "Negócios, macroeconomia, empreendedorismo e sustentabilidade."),
            p("Money Times", "https://www.moneytimes.com.br/feed/", "Foco em ações, dividendos, fundos imobiliários e investimentos."),
            p("Forbes Brasil", "https://forbes.com.br/feed/", "Negócios, bilionários, inovação e estilo de vida executivo.")
        ),
        sub("Mundo", 
            e("Bloomberg Markets", "https://feeds.bloomberg.com/markets/news.rss", "Notícias financeiras globais e mercados de capital."),
            e("CNBC", "https://www.cnbc.com/id/100003114/device/rss/rss.html", "Economia americana e corporativa mundial."),
            e("Financial Times", "https://www.ft.com/rss/home", "Jornalismo econômico e diplomacia financeira internacional.")
        )
    ),

    cat("Cripto & Web3",
        sub("Brasil", 
            p("Portal do Bitcoin", "https://portaldobitcoin.uol.com.br/feed/", "O maior portal focado em criptoativos e finanças descentralizadas do Brasil."),
            p("Cointelegraph Brasil", "https://br.cointelegraph.com/rss", "Notícias sobre blockchain, Bitcoin, Ethereum e Web3 em português.")
        ),
        sub("Mundo", 
            e("CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/", "Referência global em notícias sobre criptomoedas e mercados globais."),
            e("Decrypt", "https://decrypt.co/feed", "Cultura Web3, cripto e descentralização."),
            e("Bitcoin Magazine", "https://bitcoinmagazine.com/.rss/full/", "Foco exclusivo no ecossistema e protocolo do Bitcoin.")
        )
    ),

    // -------------------------------------------------------------
    // 10. CIÊNCIA, PROGRAMAÇÃO, CARROS & SAÚDE
    // -------------------------------------------------------------
    cat("Ciência & Espaço",
        sub("Brasil", p("Super Interessante", "https://super.abril.com.br/feed/"), p("Jornal da USP", "https://jornal.usp.br/feed/")),
        sub("Mundo", e("NASA", "https://www.nasa.gov/rss/dyn/breaking_news.rss"), e("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml"), e("Nature", "https://www.nature.com/nature.rss"))
    ),

    cat("Programação & Dev",
        sub("Desenvolvimento", e("Dev.to", "https://dev.to/feed"), e("GitHub Blog", "https://github.blog/feed/"), e("Hacker News", "https://news.ycombinator.com/rss")),
        sub("Segurança", e("Krebs on Security", "https://krebsonsecurity.com/feed/"), e("The Hacker News", "https://feeds.feedburner.com/TheHackersNews"))
    ),

    cat("Carros & Mobilidade",
        sub("Brasil", p("Quatro Rodas", "https://quatrorodas.abril.com.br/feed/"), p("Flatout", "https://www.flatout.com.br/feed/")),
        sub("Mundo", e("Car and Driver", "https://www.caranddriver.com/rss/all.xml/"), e("MotorTrend", "https://www.motortrend.com/feed/"), e("Electrek", "https://electrek.co/feed/"))
    ),

    cat("Saúde & Bem-Estar",
        sub("Brasil", p("Drauzio Varella", "https://drauziovarella.uol.com.br/feed/"), p("Veja Saúde", "https://saude.abril.com.br/feed/")),
        sub("Mundo", e("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml"), e("WHO News", "https://www.who.int/rss-feeds/news-english.xml"))
    )
)
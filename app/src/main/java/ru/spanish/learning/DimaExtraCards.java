package ru.spanish.learning;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** One-time supplemental vocabulary exclusively for Dima's Spanish database. */
final class DimaExtraCards {
    private DimaExtraCards() {}
    private static final String DATA = """
admitir|признавать|1
advertir|предупреждать|1
afirmar|утверждать|1
agregar|добавлять|1
aguantar|терпеть|1
alentar|подбадривать|1
analizar|анализировать|1
anotar|записывать|1
aplaudir|аплодировать|1
apoyar|поддерживать|1
aprovechar|использовать с пользой|1
aprobar|одобрять, сдавать экзамен|1
arriesgar|рисковать|1
atender|обслуживать, отвечать|1
atraer|привлекать|1
aumentar|увеличивать|1
avanzar|продвигаться|1
barrer|подметать|1
bloquear|блокировать|1
brillar|сиять|1
calcular|вычислять|1
calmar|успокаивать|1
castigar|наказывать|1
charlar|болтать|1
chocar|сталкиваться|1
colaborar|сотрудничать|1
colocar|размещать|1
comentar|комментировать|1
competir|соревноваться|1
completar|завершать, заполнять|1
comprobar|проверять|1
concentrarse|сосредоточиваться|1
confiar|доверять|1
conservar|сохранять|1
consultar|спрашивать, консультироваться|1
convencer|убеждать|1
corregir|исправлять|1
costar|стоить|1
criticar|критиковать|1
cumplimentar|заполнять бланк|1
debatir|обсуждать, спорить|1
decidir|решать|1
defender|защищать|1
demostrar|доказывать|1
depender|зависеть|1
describir|описывать|1
desear|желать|1
despedirse|прощаться|1
detectar|обнаруживать|1
detener|останавливать|1
discutir|спорить|1
distinguir|различать|1
dudar|сомневаться|1
empujar|толкать|1
encargar|заказывать, поручать|1
enfrentar|сталкиваться с|1
engañar|обманывать|1
entregar|вручать, доставлять|1
escapar|сбегать|1
esconder|прятать|1
evitar|избегать|1
exigir|требовать|1
fallar|ошибаться, давать сбой|1
fijarse|обращать внимание|1
firmar|подписывать|1
frenar|тормозить|1
fundar|основывать|1
herir|ранить|1
huir|убегать|1
identificar|опознавать|1
impedir|препятствовать|1
incluir|включать|1
indicar|указывать|1
influir|влиять|1
insistir|настаивать|1
investigar|исследовать|1
juntar|собирать вместе|1
lanzar|бросать, запускать|1
lidiar|справляться|1
limitar|ограничивать|1
lograr|добиваться|1
luchar|бороться|1
medir|измерять|1
mentir|лгать|1
merecer|заслуживать|1
negociar|вести переговоры|1
obligar|обязывать|1
ocultar|скрывать|1
organizar|организовывать|1
participar|участвовать|1
pertenecer|принадлежать|1
planear|планировать|1
proteger|защищать|1
quejarse|жаловаться|1
rechazar|отвергать|1
reclamar|требовать, предъявлять претензию|1
resolver|решать проблему|1
revisar|проверять, пересматривать|1
sugerir|предлагать, советовать|1
valorar|ценить, оценивать|1
acuerdo|соглашение|2
actitud|отношение, позиция|2
actividad|деятельность|2
advertencia|предупреждение|2
alquiler|аренда|2
ambiente|обстановка, среда|2
amistad|дружба|2
anuncio|объявление|2
apoyo|поддержка|2
apuro|спешка|2
argumento|довод|2
asunto|дело, вопрос|2
atención|внимание|2
aumento|повышение|2
autoridad|власть, орган власти|2
avance|прогресс|2
beneficio|выгода, польза|2
bronca|злость|2
calidad|качество|2
cambio|изменение|2
cantidad|количество|2
capacidad|способность|2
carácter|характер|2
causa|причина|2
certeza|уверенность|2
cita|встреча, запись на приём|2
comercio|торговля|2
compañero|товарищ, одноклассник|2
competencia|соревнование, конкуренция|2
compromiso|обязательство|2
comunidad|сообщество|2
consecuencia|последствие|2
consejo|совет|2
consumo|потребление|2
contrato|договор|2
control|контроль|2
costumbre|привычка, обычай|2
crecimiento|рост|2
crítica|критика|2
culpa|вина|2
decisión|решение|2
demora|задержка|2
desafío|вызов, сложная задача|2
desarrollo|развитие|2
descuento|скидка|2
deseo|желание|2
destino|пункт назначения, судьба|2
detalle|подробность|2
diferencia|разница|2
dificultad|трудность|2
disculpa|извинение|2
discusión|спор|2
duda|сомнение|2
educación|образование|2
efecto|эффект|2
elección|выбор|2
emergencia|чрезвычайная ситуация|2
empresa|компания|2
energía|энергия|2
engaño|обман|2
entorno|окружение|2
equipo|команда, оборудование|2
error|ошибка|2
esfuerzo|усилие|2
espacio|пространство|2
etapa|этап|2
experiencia|опыт|2
explicación|объяснение|2
éxito|успех|2
falla|неисправность|2
favor|услуга, одолжение|2
fecha|дата|2
fuerza|сила|2
gasto|расход|2
gobierno|правительство|2
habilidad|навык|2
hecho|факт, событие|2
herramienta|инструмент|2
impuesto|налог|2
información|информация|2
informe|отчёт|2
intención|намерение|2
interés|интерес|2
jefe|начальник|2
juicio|судебное разбирательство|2
límite|предел|2
logro|достижение|2
mentira|ложь|2
meta|цель|2
miedo|страх|2
motivo|причина, мотив|2
necesidad|потребность|2
oportunidad|возможность|2
opinión|мнение|2
orden|порядок, приказ|2
permiso|разрешение|2
plazo|срок|2
riesgo|риск|2
trámite|официальная процедура|2
solución|решение проблемы|2
actual|нынешний|3
adecuado|подходящий|3
ajeno|чужой|3
amplio|просторный, широкий|3
ansioso|тревожный, нетерпеливый|3
apropiado|уместный|3
atento|внимательный|3
atractivo|привлекательный|3
auténtico|подлинный|3
automático|автоматический|3
básico|основной, базовый|3
brillante|блестящий|3
capaz|способный|3
casual|случайный|3
cierto|определённый, верный|3
complicado|сложный|3
concreto|конкретный|3
confiable|надёжный|3
confuso|непонятный, запутанный|3
constante|постоянный|3
conveniente|удобный, целесообразный|3
creativo|творческий|3
cruel|жестокий|3
cuidadoso|осторожный|3
curioso|любопытный|3
decente|приличный|3
definitivo|окончательный|3
delicado|нежный, деликатный|3
desagradable|неприятный|3
desconocido|неизвестный|3
desesperado|отчаявшийся|3
desordenado|неопрятный, беспорядочный|3
destacado|выдающийся|3
detallado|подробный|3
directo|прямой|3
dispuesto|готовый, расположенный|3
diverso|разнообразный|3
doble|двойной|3
económico|экономичный, недорогой|3
efectivo|действенный|3
eficiente|эффективный|3
egoísta|эгоистичный|3
elegante|элегантный|3
emocionante|захватывающий|3
enorme|огромный|3
entero|целый|3
equilibrado|сбалансированный|3
esencial|существенный|3
estable|стабильный|3
estricto|строгий|3
exacto|точный|3
exagerado|преувеличенный|3
excelente|отличный|3
excesivo|чрезмерный|3
exigente|требовательный|3
extraño|странный|3
familiar|знакомый, семейный|3
flexible|гибкий|3
frecuente|частый|3
frustrado|расстроенный|3
fundamental|основополагающий|3
generoso|щедрый|3
grave|серьёзный, тяжёлый|3
habitual|привычный|3
honesto|честный|3
horrendo|ужасный|3
ideal|идеальный|3
idéntico|идентичный|3
increíble|невероятный|3
independiente|независимый|3
indispensable|незаменимый|3
injusto|несправедливый|3
inmediato|немедленный|3
inquieto|беспокойный|3
intenso|интенсивный|3
interno|внутренний|3
justo|справедливый|3
laboral|трудовой|3
legal|законный|3
limitado|ограниченный|3
maduro|зрелый|3
mínimo|минимальный|3
modesto|скромный|3
múltiple|множественный|3
obligatorio|обязательный|3
oficial|официальный|3
optimista|оптимистичный|3
original|оригинальный|3
paciente|терпеливый|3
particular|особый, конкретный|3
pendiente|ожидающий решения|3
práctico|практичный|3
preciso|точный|3
probable|вероятный|3
profundo|глубокий|3
responsable|ответственный|3
urgente|срочный|3
valioso|ценный|3
transparente|прозрачный|3
voluntario|добровольный|3
""";
    static int seed(SQLiteDatabase db) {
        Set<String> present = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT spanish FROM cards WHERE kind=0", null)) {
            while (c.moveToNext()) present.add(c.getString(0).trim().toLowerCase(Locale.ROOT));
        }
        int inserted = 0;
        for (String line : DATA.strip().split("\\R")) {
            String[] fields = line.strip().split("\\|", 3);
            if (fields.length != 3) throw new IllegalStateException("Invalid Dima card");
            if (!present.add(fields[0].toLowerCase(Locale.ROOT))) continue;
            ContentValues v = new ContentValues();
            v.put("spanish", fields[0]); v.put("meaning", fields[1]);
            v.put("kind", 0); v.put("category", Integer.parseInt(fields[2]));
            v.put("mastered", 0);
            db.insertOrThrow("cards", null, v);
            inserted++;
        }
        return inserted;
    }
}

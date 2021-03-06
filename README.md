# Spirit 3
Fork of [mikereidis/spirit2_free](https://github.com/mikereidis/spirit2_free) only for QualComm based processors.

![Screens of application](https://raw.githubusercontent.com/vladislav805/Spirit3-FM/master/docs_images/screens.png)
![Screen of notification](https://raw.githubusercontent.com/vladislav805/Spirit3-FM/master/docs_images/screen-record.png)

## Changelog
#### build 20180930 (3.0.6)
* Исправлена проблема с забыванием включения микрофона после завершения радио на некоторых девайсах.

#### build 20180925 (3.0.5)
* Исправлена проблема с выключением микрофона после завершения радио \[как позднее выяснилось -- не исправлено\];
* Добавлен пробный тест низкоуровневой части;
* Улучшения кода внутри приложения;
* Добавление настроки "писать логи".

#### build 20180913 (3.0.4)
* Переделана коряво сделанная линейка с частотой;
* Добавлена страница с настройками и отладочной информацией (открывается долгим зажатием по частоте).

#### build 20180907 (3.0.3)
* Появился и начал работать переключатель наушники/динамики (ура); 
* Главный экран претерпел небольшие изменения:
    * Убран визуализатор (временно);
    * Кнопки сдвига на -0.1MHz и +0.1MHz передвинуты на строку с текущей частотой;
    * Шкала частот выровнена идеально;
    * Статусбар перекрашен под фон приложения;
    * Если станция есть в пресетах и у нее указано имя, то под текущей частотой отображается название;
* Лимит названия в пресетах увеличен с 8 до 10 символов.

#### build 20180906 (3.0.2) ([4pda](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=76756386))
* Перекрашен текст в Toast-ах (не спрашивайте зачем);
* Исправлено отображение кнопок и иконок при выключенном радио;
* В пресетах теперь показывается одновременно частота и название.

#### build 20180905 (3.0.1) ([4pda](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=76716257))
> Изменена подпись из-за моей криворукости. Перед установкой версий 3.0.1 и выше, если была установлена 3.0, нужно удалить старую

* Поднят minSDK до 21 (Android 5.0);
* Увеличено количество пресетов до 30;
* Немного приведен в порядок экран:
    * Уменьшен размер текста на кнопках пресетов;
    * Иконка записи передвинута вправо;
    * Включатель визуализатора обзавелся иконкой;
* Управление пресетов теперь выполняется через Popup
* Теперь пресетам можно давать имена. Имя может быть до 8 символов;


#### build 20180620 (3.0) ([4pda](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=74367538))
* Убран звук микрофона из эфира на некоторых устройствах

#### build 20180316 ([4pda](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=71410712))
* Добавлена русская локализация

#### build 20180315 ([4pda](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=71343076))
* Поднят minSdk с 16 до 21 (Android 5.0);
* Вырезаны все библиотеки от всех устройств, кроме устройств на процессорах QualComm;
* Изменен элемент выбора частоты: вместо круга, сделан горизонтальный ScrollView с полосой выбора частоты;
* В Free-исходнике не было записи и визуализатора -- восстановлено;
* Усовершенствовано уведомление в шторке уведомлений: кнопки Play/Pause, Stop и запись;
* Запись эфира производится по пути `~/Music/fm/{YYYY}-{MM}-{DD}/FM-{FREQ}-{HH}{mm}{SS}.wav`;
* Переделан весь главный экран:
    * Для того, чтобы выключить радио (а не поставить на паузу) нужно зажать паузу (ранее была отдельная кнопка, убрал);
    * Огромное количество графики заменено на вектор;
    * Обновлен стартовый экран;
    * Увеличено количество пресетов, теперь они динамически создаются;
    * Заменен шрифт частоты на главном экране, при нажатии на нее, открывается окно с текущей частотой; 
* Изменен формат RSSI (колеблется между 0-64) + иконка качества сигнала.

## Официально работает

| ? | Устройство | ОС | Версия ОС | QSD | MSM |  
|:-:| ---------- | -- |:-------------:|:---:|:---:|
| ☑ | Xiaomi Mi A1 | AOSP | 7.1.2 | 625 | 8953 |
| ☑ | Xiaomi Mi A1 | AOSP | 8.1 | 625 | 8953 |
| x | Xiaomi Mi A1 | LineageOS 15.1 | 8.1 | 625 | 8953 |
| ☑ | Xiaomi Mi Max | MIUI ? | 7.x | 650 | 8956 |
| ☑ | Xiaomi Mi A2 Lite \[magisk\] | AOSP | 8.1 | 625 | 8953 |
| ☑ | Xiaomi Redmi Note 4 (Mido) | MIUI 9.0 | ? | 625 | 8953 |
| ? | Xiaomi Redmi Note 4X | ? | ? | 625 | 8953 |
| ☑ | Xiaomi Redmi 5 Plus | ? | 8.1 | 625 | 8953 |
| ☑ | Sony Xperia L | CyanogenMod 12.1 | 5.1 | S4 Plus | 8230 |
| □ | YotaPhone 2 | AOSP | 6.0.1 | 801 | 8974 |
| ? | ZTE Nubia Z7 Mini | ? | ? | 801 | 8974AA |
| □ | ZTE Nubia Z11 Mini S | ? | ? | 617 | 8952 |
| ☑ | AGM A8 | AOSP | ? | 410 | 8916 |
| ☑ | Samsung Galaxy S4 Mini I9195 | ? | 9.0 | 400 | 8230AB |
| ☑ | Lenovo Vibe K5 Plus | CyanogenMod 12.1 | 5.1 | 616 | 8939v2 |

| Иконка | Описание |
|:------:| -------- |
| ☑ | всё работает |
| □ | есть незначительные ошибки |
| ? | неизвестно (но работала предыдущая версия модификации) |

> Таблица составлена по данным от добровольцев форума 4pda, кроме Mi A1 и Xperia L (они есть у меня).

## 4PDA
Все релизы и установочные файлы публикуются на 4pda: [пост в теме форума](http://4pda.ru/forum/index.php?showtopic=543819&view=findpost&p=76841416). Для скачивания apk требуется авторизация (иначе 404).
      
## Известные проблемы
* Snapdragon 636 и 801, кажется, не поддерживается полностью (используют не /dev/radio0, а выпиленный способ от BCH; Texas Instruments).
* На ZTE Nubia Z11 Mini S хрипит, при выключении стоковое радио работает через динамик. 


## Feedback

Все неравнодушные, желающие помочь, те, кто девайсами на процессорах QualComm.
Просьба написать мне в [QMS 4pda](https://4pda.ru/forum/index.php?act=qms&mid=3786628) с темой **"Spirit 3 на `%device_name%`"** или мне в [Telegram](tg://resolve?domain=vladislav805) если моя сборка Spirit3:
* **работает**: версию ОС (и указать, если это **не AOSP**: CyanogenMod/LinageOS, MIUI, etc)

    В таком случае я добавлю Ваше устройство в список устройств на котором приложение "официально" работает.

* **не работает**: версию ОС (и указать, если это **не AOSP**: CyanogenMod/LinageOS, MIUI, etc) + ***LogCat*** (приложить или скрины, или txt-файл)
  ##### Инструкция по снятию LogCat:
    * Запустить сбор логов (запустить приложение или вбить команду adb на ПК)
    * Почти сразу запустить Spirit3: именно запустить, проверить чтобы его не было на экране многозадачности и нотификации/сервиса в уведомлениях - в таком случае остановить через настройки Android -> приложения -> Spirit3 -> Force stop/Остановить принудительно
    * После ~15 секунд "бесконечной загрузки" закончить сбор логов. Можно закрыть Spirit.
  ###### Команда для Linux
  ```bash
  adb logcat > log.txt
  ```
  Для остановки сбора (клавиша по умолчанию) -- Ctrl+C
  LogCat: 
  Если скрины, то чтобы не скринить всё подряд -- фильтрация по следующим тегам (без кавычек):
    * `s2tnr_qcv`
    * `sfcom`
    * `s2d......`
    
## Contributions
* Author: [Mike Reid](https://github.com/mikereidis) RIP ![Rest in peace](https://cs5-3.4pda.to/11327050.gif) (c) 2010-2015
* Modification: [Vladislav Veluga](https://github.com/vladislav805) (c) 2018
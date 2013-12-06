#include <SPI.h>

#define GAIN_SETTING_ERROR B000

#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))

uint8_t low=0, high=0;

const int analogInPin = A0;    // Pin de entrada analogico
const int chipSelectPin = 10;  // Pin de chip select para el amplificador operacional

// Definicion de los prescalers
const unsigned char PS_2 = (1 << ADPS0);
const unsigned char PS_4 = (1 << ADPS1);
const unsigned char PS_8 = (1 << ADPS1) | (1 << ADPS0);
const unsigned char PS_16 = (1 << ADPS2);
const unsigned char PS_32 = (1 << ADPS2) | (1 << ADPS0);
const unsigned char PS_64 = (1 << ADPS2) | (1 << ADPS1);
const unsigned char PS_128 = (1 << ADPS2) | (1 << ADPS1) | (1 << ADPS0);

void setup() {
  
  configureSPI();
  
  // Inicializa el puerto serie en 2Mbaudios
  Serial.begin(2000000);
  
  // Ganancia por defecto = x10
  setGain(0x10);
  configureADC();
  
  while(true) {
        // start the conversion
        sbi(ADCSRA, ADSC);
        
        if(Serial.available() > 0) {
          setGain(Serial.read());
        }

        UDR0 = high; //(high << 6) | (low >> 2);
        // ADSC is cleared when the conversion finishes
        while (bit_is_set(ADCSRA, ADSC));

        // we have to read ADCL first; doing so locks both ADCL
        // and ADCH until ADCH is read.  reading ADCL second would
        // cause the results of each conversion to be discarded,
        // as ADCL and ADCH would be locked when it completed.
        low  = ADCL;
        high = ADCH;
    }
}

// Configuracion del ADC
void configureADC() {
  ADCSRA &= ~PS_128;  // Se eliminan los bits seteados por la libreria de Arduino
  
  ADCSRA |= PS_4;    // Se setea un valor de prescaler 

  // set the analog reference (high two bits of ADMUX) and select the
  // channel (low 4 bits).  this also sets ADLAR (left-adjust result)
  // to 0 (the default).
  ADMUX = (EXTERNAL << 6) | 1 << 5 | (0 & 0x07);
}

// Configuracion del modulo de comunicacion SPI
void configureSPI() {
  pinMode(chipSelectPin, OUTPUT); // Pin de chip select como salida digital
  SPI.begin();
  SPI.setBitOrder(MSBFIRST);  // Orden: Bit mas significativo primero
  
  // Modo 0:
  // Polaridad: base del clock es el cero
  // Informacion es capturada en el flanco positivo del reloj
  SPI.setDataMode(SPI_MODE0);
}

void loop() {
}


// Se envia el valor de ganancia al amplificador operacional programable
int setGain(byte gain) {
  byte gain_bits = 0;

  switch(gain){
    case 0x01:
      gain_bits = B000;
      break;
    case 0x02:
      gain_bits = B001;
      break;
    case 0x04:
      gain_bits = B010;
      break;
    case 0x05:
      gain_bits = B011;
      break;
    case 0x08:
      gain_bits = B100;
      break;
    case 0x10:
      gain_bits = B101;
      break;
    case 0x16:
      gain_bits = B110;
      break;
    case 0x32:
      gain_bits = B111;
      break;
    default:
      return GAIN_SETTING_ERROR;
  }
  digitalWrite(chipSelectPin, LOW);   // Se habilita el operacional para escucha
  SPI.transfer(B01000000);            // Se selecciona el registro de ganancia
  SPI.transfer(gain_bits);            // Se envia el valor de ganancia
  digitalWrite(chipSelectPin, HIGH);  // Se libera el operacional
  
  return 0;
}



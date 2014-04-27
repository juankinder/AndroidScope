#include <SPI.h>

#define GAIN_SETTING_ERROR B000

#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))

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

uint8_t high=0;
uint8_t dc=0;
float vcc;

void setup() {
  
  configureSPI();
  
  // Inicializa el puerto serie en 2Mbaudios
  Serial.begin(2000000);
  
  // Ganancia por defecto = x10
  setGain(0x10);
  //getVcc();
  configureADC();
  
  // Inicia la conversion
  sbi(ADCSRA, ADSC);
        
  while(true) {
        // Inicia la conversion
        sbi(ADCSRA, ADSC);
        
        if(Serial.available() > 0) {
          setGain(Serial.read());
        }

        UDR0 = high; 
        // ADSC se pone en cero cuando la conversion finalize
        while (bit_is_set(ADCSRA, ADSC));

        // Se lee el valor convertido
        high = ADCH - dc;
    }
}

void getVcc() {
  // Se configura el ADC para que utilice la tension de referencia externa,
  // el resultado este alineado a la izquierda y
  // se selecciona el canal 0 del ADC.
  ADMUX = B01111110;
  
  sbi(ADCSRA, ADSC);
  while (bit_is_set(ADCSRA, ADSC));
  // Se lee el valor convertido
  vcc =  1.1 * 255 / ADCH;
}

// Configuracion del ADC
void configureADC() {
  ADCSRA &= ~PS_128;  // Se eliminan los bits seteados por la libreria de Arduino
  
  ADCSRA |= PS_4;    // Se setea un valor de prescaler 
  
  // Set ADEN in ADCSRA (0x7A) to enable the ADC.
  // Note, this instruction takes 12 ADC clocks to execute
  //sbi(ADCSRA, ADEN);
  
  // Set ADATE in ADCSRA (0x7A) to enable auto-triggering.
  //sbi(ADCSRA, ADATE);
  
  // Clear ADTS2..0 in ADCSRB (0x7B) to set trigger mode to free running.
  // This means that as soon as an ADC has finished, the next will be
  // immediately started.
  //cbi(ADCSRB, ADTS0);
  //cbi(ADCSRB, ADTS1);
  //cbi(ADCSRB, ADTS2);

  // Se configura el ADC para que utilice la tension de referencia externa,
  // el resultado este alineado a la izquierda y
  // se selecciona el canal 0 del ADC.
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
  dc = 0;

  switch(gain){
    case 0x01:
      gain_bits = B000;
      //dc = -3;
      break;
    case 0x02:
      gain_bits = B001;
      //dc = -2;
      break;
    case 0x04:
      gain_bits = B010;
      //dc = -1;
      break;
    case 0x05:
      gain_bits = B011;
      //dc = 1;
      break;
    case 0x08:
      gain_bits = B100;
      //dc = 2;
      break;
    case 0x10:
      gain_bits = B101;
      //dc = 4;
      break;
    case 0x16:
      gain_bits = B110;
      //dc = 8;
      break;
    case 0x32:
      gain_bits = B111;
      //dc = 22;
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



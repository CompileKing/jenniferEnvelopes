import com.cycling74.max.*;
import javax.sound.midi.*;

import java.io.*;

public class mfplay extends MaxObject
{
	String path;
	Sequence sequence = null;
	Receiver	sm_receiver = new DumpReceiver(this, false);
	//Receiver sm_receiver = new FakeReceiver(this);
	private static Sequencer sm_sequencer = null;
	//private static Synthesizer	sm_synthesizer = null;
	File midiFile;
	boolean loop;
	
	private static final String[] INLET_ASSIST = new String[]{
		"(anything) see Usage"
	};
	private static final String[] OUTLET_ASSIST = new String[]{
		"(symbol) ShortMessages (Note on/off, control change...)", "(Symbol) MetaEvents", "(Symbol) File Infos","(bang) on end/loop end", "(bang) when loaded"
	};
	
	public mfplay()
	{
		declareIO(1,5);
		setInletAssist(INLET_ASSIST);
		setOutletAssist(OUTLET_ASSIST);
		createInfoOutlet(false);
		
		post("\n......................................................................................");
		post("[mxj mfplay] 0.1");
		post("(c) 2006 by f.e chanfrault");
		post("(c) 1999 - 2001 by Matthias Pfisterer");
        post("(c) 2003 by Florian Bomers");
		post("......................................................................................");
	}
	
	public void open (String[] args)
	{
		if (args.length==0)
		{
			path = MaxSystem.openDialog();
			// split the open() function in order to handle the path = null from cancelling the opendialog()
		}
		else
		{
			path = args[0];
		}
		// create the file object
		midiFile = new File(path);
		String fileExt = getFileExtension(path);
		if(!midiFile.exists() || midiFile.isDirectory() || !midiFile.canRead() || !fileExt.equalsIgnoreCase(".mid")) 
		{
            post ("[mfplay] error >: can't open "+ midiFile.getName()+" !");
            midiFile = null;
        }
		else
		{
			try 
			{
				// get MidiFileFormat properties
				MidiFileFormat fileFormat=MidiSystem.getMidiFileFormat(midiFile);
				String fileName = midiFile.getName();
				//String filePath = midiFile.getPath();
				post ("FileName : "+fileName);
				outlet(2, "FileName: "+fileName);
				post ("Type : "+fileFormat.getType());
				outlet(2, "Type: "+fileFormat.getType());
				post ("Size : "+fileFormat.getByteLength()+" bytes");
				outlet(2, "Size: "+fileFormat.getByteLength()+" bytes");
			} catch (InvalidMidiDataException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try
			{
				// create the sequence
				sequence = MidiSystem.getSequence(midiFile);
				outletBang(4);
			}
			catch (InvalidMidiDataException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			// get sequence properties
			long tickLength = sequence.getTickLength();
			long microLength = sequence.getMicrosecondLength();
			
			post("Length: " + tickLength + " ticks");
			outlet(2, "Length: " + tickLength + " ticks");
			post("Duration: " + microLength/1000. + " milliseconds");
			outlet(2, "Duration: " + microLength/1000. + " milliseconds");
			
			float	fDivisionType = sequence.getDivisionType();
			String	strDivisionType = null;
			
			// get the Timing Division Type
			if (fDivisionType == Sequence.PPQ)
			{
				strDivisionType = "PPQ";
			}
			else if (fDivisionType == Sequence.SMPTE_24)
			{
				strDivisionType = "SMPTE, 24 frames per second";
			}
			else if (fDivisionType == Sequence.SMPTE_25)
			{
				strDivisionType = "SMPTE, 25 frames per second";
			}
			else if (fDivisionType == Sequence.SMPTE_30DROP)
			{
				strDivisionType = "SMPTE, 29.97 frames per second";
			}
			else if (fDivisionType == Sequence.SMPTE_30)
			{
				strDivisionType = "SMPTE, 30 frames per second";
			}

			post("DivisionType: " + strDivisionType);
			outlet(2, "DivisionType: " + strDivisionType);
		
			String	strResolutionType = null;
			
			if (sequence.getDivisionType() == Sequence.PPQ)
			{
				strResolutionType = " ticks per beat";
			}
			else
			{
				strResolutionType = " ticks per frame";
			}
			post("Resolution: " + sequence.getResolution() + strResolutionType);
			outlet(2, "Resolution: " + sequence.getResolution() + strResolutionType);
			
			Track[]	tracks = sequence.getTracks();
			outlet(2, "NumberOfTracks: "+tracks.length);

			post("......................................................................................");
		}
		
	} // end of function open()
	
	public void dump() throws MidiUnavailableException
	{
		if (midiFile == null)
		{
			post ("[mfplay] error >: Open a file first !");
		}
		else
		{
			Track[]	tracks = sequence.getTracks();
			for (int nTrack = 0; nTrack < tracks.length; nTrack++)
			{
				post("Track " + nTrack + ":");
				post("-----------------------");
				Track	track = tracks[nTrack];
				for (int nEvent = 0; nEvent < track.size(); nEvent++)
				{
					MidiEvent	event = track.get(nEvent);
					output(event);
				}
			}
			post("......................................................................................");
		}
	}
	
	private static String getFileExtension(String f)
	{
		int dotPos = f.lastIndexOf(".");
        return f.substring(dotPos);
	}
	
	// dump the midiFile using DumpReceiver.class
	public void output(MidiEvent event) throws MidiUnavailableException
	{
		MidiMessage	message = event.getMessage();
		long		lTicks = event.getTick();
		sm_receiver.send(message, lTicks);
	}
	
	public void play()
	{
		if (midiFile!=null)
		{
			try
			{
				// ici, je magouille pour définir moi-même le device Real Time Sequencer
				// il semble que aucun synthé ne s'y associe automatiquement
				// et que donc, pas de son... ouf !
				
				//sm_sequencer = MidiSystem.getSequencer(); // deviens :
				MidiDevice.Info[]	aInfos = MidiSystem.getMidiDeviceInfo();
				sm_sequencer = (Sequencer) MidiSystem.getMidiDevice(aInfos[20]);
				
				// il semblerait que la methode suivante marche aussi :
				// sequencer = MidiSystem.getSequencer(false);
				// ici, "false" conduirait à ne pas connecter le sequencer à un synth par defaut
				post("Connected to : "+sm_sequencer.getDeviceInfo().toString());
			}
			catch (MidiUnavailableException e)
			{
				e.printStackTrace();
			}
			if (sm_sequencer == null)
			{
				post ("[mfplay] error >: can't get a Sequencer");
			}
			else
			{
				// this is something !
				// it seems the "Real Time Sequencer" doesn't take care of MetaEvents alone
				// so we have to tell him to do so
				sm_sequencer.addMetaEventListener
					(new MetaEventListener()
						{
							public void meta(MetaMessage event)
							{
								//byte[]	abMessage = event.getMessage();
								byte[]	abData = event.getData();
								//int	nDataLength = event.getLength();
								String	strMessage = null;
								// System.out.println("data array length: " + abData.length);
								switch (event.getType())
								{
								case 0:
									int	nSequenceNumber = ((abData[0] & 0xFF) << 8) | (abData[1] & 0xFF);
									strMessage = "SequenceNumber " + nSequenceNumber;
									break;

								case 1:
									String	strText = new String(abData);
									strMessage = "TextEvent " + strText;
									break;

								case 2:
									String	strCopyrightText = new String(abData);
									strMessage = "CopyrightNotice " + strCopyrightText;
									break;

								case 3:
									String	strTrackName = new String(abData);
									strMessage = "Sequence/Track Name " +  strTrackName;
									break;

								case 4:
									String	strInstrumentName = new String(abData);
									strMessage = "InstrumentName " + strInstrumentName;
									break;

								case 5:
									String	strLyrics = new String(abData);
									strMessage = "Lyric " + strLyrics;
									break;

								case 6:
									String	strMarkerText = new String(abData);
									strMessage = "Marker " + strMarkerText;
									break;

								case 7:
									String	strCuePointText = new String(abData);
									strMessage = "CuePoint " + strCuePointText;
									break;

								case 0x20:
									int	nChannelPrefix = abData[0] & 0xFF;
									strMessage = "MIDIChannelPrefix " + nChannelPrefix;
									break;

								case 0x2F:
									strMessage = "EndofTrack";
									outletBang(3);
									break;
									
								case 0x51:
									int	nTempo = ((abData[0] & 0xFF) << 16)
											| ((abData[1] & 0xFF) << 8)
											| (abData[2] & 0xFF);           // tempo in microseconds per beat
									float bpm = DumpReceiver.convertTempo(nTempo);
									// truncate it to 2 digits after dot
									bpm = (float) (Math.round(bpm*100.0f)/100.0f);
									strMessage = "SetTempo "+bpm+" bpm";
									outletBang(3);
									break;

								case 0x54:
									// System.out.println("data array length: " + abData.length);
									strMessage = "SMTPE_Offset "
										+ (abData[0] & 0xFF) + ":"
										+ (abData[1] & 0xFF) + ":"
										+ (abData[2] & 0xFF) + "."
										+ (abData[3] & 0xFF) + "."
										+ (abData[4] & 0xFF);
									break;

								case 0x58:
									strMessage = "TimeSignature "
										+ (abData[0] & 0xFF) + "/" + (1 << (abData[1] & 0xFF))
										+ ", MIDI clocks per metronome tick: " + (abData[2] & 0xFF)
										+ ", 1/32 per 24 MIDI clocks: " + (abData[3] & 0xFF);
									break;

								case 0x59:
									String	strGender = (abData[1] == 1) ? "minor" : "major";
									strMessage = "KeySignature " + DumpReceiver.sm_astrKeySignatures[abData[0] + 7] + " " + strGender;
									break;

								case 0x7F:
									// TODO: decode vendor code, dump data in rows
									String	strDataDump = DumpReceiver.getHexString(abData);
									strMessage = "Sequencer-Specific" + strDataDump;
									break;

								default:
									String	strUnknownDump = DumpReceiver.getHexString(abData);
									strMessage = "unknown " + strUnknownDump;
									break;

								}
								outlet(1, strMessage);
								}
							}
						
					);
				
				try
				{
					sm_sequencer.open();
					post("Default Sequencer opened");
				}
				catch (MidiUnavailableException e)
				{
					e.printStackTrace();
					post ("[mfplay] error >: can't open Sequencer");
				}
				try
				{
					sm_sequencer.setSequence(sequence);
					post("Sequence set to Sequencer");
				}
				catch (InvalidMidiDataException e)
				{
					e.printStackTrace();
					post ("[mfplay] error >: can't set Sequence to Sequencer");
				}
				if (! (sm_sequencer instanceof Synthesizer))
				{
					try
					{
						// use the DumpReceiver to print out the midi events !
						Transmitter	seqTransmitter = sm_sequencer.getTransmitter();
						//sm_synthesizer = new NoSynth(); 
						seqTransmitter.setReceiver(sm_receiver);				
					}
					catch (MidiUnavailableException e)
					{
						e.printStackTrace();
					}
				} else post("sm_sequencer is also a synthesizer");
				
				closeMidiDev(20);
				sm_sequencer.start();
				if (loop)
				{
					sm_sequencer.setLoopStartPoint(0);
					sm_sequencer.setLoopEndPoint(-1);
					sm_sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
				}
			}
		}
		else
		{
			post ("[mfplay] error >: Open a file first !");
		}
	}
	public void stop()
	{
		if (sm_sequencer!=null)
		{
			sm_sequencer.close();
			outletBang(3);
			post("Sequencer stopped");
		}
		else
		{
			post ("[mfplay] error >: Open a file first !");
		}
	}
	
	public void debug()
	{
		boolean test = sm_sequencer instanceof Synthesizer;
		if (test)
		{
			post("True");
		}
		else post("False");
	}
	
	// shows that we can use ANY sequencer method for the future
	// mute, solo, tempo...
	public void mute(int i, int b)
	{
		boolean z = (b != 0); // convert in b to boolean z
		sm_sequencer.setTrackMute(i, z);
	}
	
	public void closeMidiDev(int dev)
	{
		MidiDevice.Info[]	aInfos = MidiSystem.getMidiDeviceInfo();
		try{
		MidiDevice	device = MidiSystem.getMidiDevice(aInfos[dev]);
		device.close();
		post("[mfplay] device "+dev+" : "+ aInfos[dev].getName()+" closed");
		} catch (MidiUnavailableException e) {
		  	post("[mfplay] problem closing device "+dev+" : "+ aInfos[dev].getName());
		  }
	}
	// problème : le dump lit le tempo mais pas le sequencer !
	// TODO : MetaEventListener pour envoyer un setTempo...
	public void getTempo() throws MidiUnavailableException
	{
		post("bpm : "+sm_sequencer.getTempoInBPM());
		//post("MPQ : "+sm_sequencer.getTempoInMPQ());
		//post("Factor :"+sm_sequencer.getTempoFactor());
	}
	public void setTempo(int t)
	{
		sm_sequencer.setTempoInBPM(t);
	}
	public void setTick(int t)
	{
		sm_sequencer.setTickPosition(t);
	}
	public void loop(int l)
	{
		loop = (l != 0);
	}

} // end of file

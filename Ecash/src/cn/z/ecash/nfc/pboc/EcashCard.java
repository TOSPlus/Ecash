/* NFCard is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

NFCard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Wget.  If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7 */

package cn.z.ecash.nfc.pboc;
import java.util.ArrayList;
import android.content.res.Resources;
import android.util.Log;

import cn.z.ecash.commn.*;
import cn.z.ecash.nfc.Iso7816;

final class EcashCard extends PbocCard {
	private final static byte[] PPSE = { 
		(byte) '2',(byte) 'P', (byte) 'A', (byte) 'Y',(byte) '.', 
		(byte) 'S', (byte) 'Y', (byte) 'S', (byte) '.',
		(byte) 'D', (byte) 'D', (byte) 'F',(byte) '0', (byte) '1'  };

	private EcashCard(Iso7816.Tag tag, Resources res) {
		super(tag);
	}

	@SuppressWarnings("unchecked")
	final static EcashCard read(Iso7816.Tag tag, Resources res) {
		Log.i("ECASH", "【start】:");

		byte[] tbyte = null;
		int toff = 0;
		short fcioff = 0;
		/*--------------------------------------------------------------*/
		// select PPSE (2PAY.SYS.DDF01)
		/*--------------------------------------------------------------*/
		Iso7816.Response ppseres = tag.selectByName(PPSE);
		Log.i("ECASH", "【select PPSE】:"+ppseres.toString());

		if (ppseres.isOkey()) {
			tbyte = ppseres.getBytes();
			toff = 0;
			byte[] DFAID = null;
			
			if((byte)0x6F == tbyte[toff++]){
				if((byte)0x81 == tbyte[toff++]){
					toff++;
				}
				short FCIoff = Utils.findValueOffByTag((short) 0xA5, 
						tbyte,(short)toff,(short)tbyte[toff-1]);
				if(FCIoff < 0)
					return  null;
				
				short FCIdefoff = Utils.findValueOffByTag((short) 0xBF0C, 
						tbyte,(short)FCIoff,(short)tbyte[FCIoff-1]);
				if(FCIoff < 0)
					return  null;
				
				short Contentoff = Utils.findValueOffByTag((short) 0x61, 
						tbyte,(short)FCIdefoff,(short)tbyte[FCIdefoff-1]);
				if(Contentoff < 0)
					return  null;
				
				short DFAIDoff = Utils.findValueOffByTag((short) 0x4F, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(DFAIDoff < 0)
					return  null;
				
				short APPlaboff = Utils.findValueOffByTag((short) 0x50, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(APPlaboff < 0)
					return  null;
				
				DFAID = new byte[tbyte[DFAIDoff - 1]];
				System.arraycopy(tbyte, DFAIDoff, DFAID, 0, DFAID.length);
				
			}else{
				return null;
			}
			Log.i("ECASH", "【AID】:" + Utils.toHexString(DFAID, 0, DFAID.length));

			
			Iso7816.Response INFO, CASH,ATC;

			/*--------------------------------------------------------------*/
			// select Main Application
			/*--------------------------------------------------------------*/
			Iso7816.Response selectdffci = tag.selectByName(DFAID);
			Log.i("ECASH", "【select Main Application】:" + selectdffci.toString());

			if (selectdffci.isOkey()) {
				tbyte = selectdffci.getBytes();
				if(tbyte == null){
					return null;
				}
				toff = 0;
				byte logsfi = 0;
				if((byte)0x6F == tbyte[toff++]){
					if((byte)0x81 == tbyte[toff++]){
						toff++;
					}
					toff = Utils.findValueOffByTag((short) 0xA5, 
							tbyte,(short)toff,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;
					int toffA5 = toff;

					//PDOL
					toff = Utils.findValueOffByTag((short) 0x9F38, 
							tbyte,(short)toff,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;
					
					
					toff = Utils.findValueOffByTag((short) 0xBF0C, 
							tbyte,(short)toffA5,(short)tbyte[toffA5-1]);
					if(toff < 0)
						return  null;
					
					toff = Utils.findValueOffByTag((short) 0x9F4D, 
							tbyte,(short)toff,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;
					
					logsfi = tbyte[toff];
				}
				Log.i("ECASH", "【LOGSFI】:" + logsfi);
				/*--------------------------------------------------------------*/
				// read card info file, binary (21)
				/*--------------------------------------------------------------*/
				INFO = tag.readRecord(2,1);
				Log.i("ECASH", "【INFO】:" + INFO.toString());

				/*--------------------------------------------------------------*/
				// read balance
				/*--------------------------------------------------------------*/
				CASH = tag.getPBOCBalance();
				Log.i("ECASH", "【BLANCE】:" + CASH.toString());

				// read ATC
				/*--------------------------------------------------------------*/
				ATC = tag.getData(0x9F36);
				Log.i("ECASH", "【ATC】:" + ATC.toString());

				// read 日志格式
				/*--------------------------------------------------------------*/
				byte[] logdol = tag.getData(0x9F4F).getBytes();
				Log.i("ECASH", "【LOGFOMAT】:"+Utils.toHexString(logdol, 0, logdol.length));

				
				/*--------------------------------------------------------------*/
				// read log file,
				/*--------------------------------------------------------------*/
				ArrayList<byte[]> LOG = readLog(tag, logsfi);
				if((LOG == null) || (LOG.size() == 0)){
					Log.i("ECASH", "【LOG】:ZERO");
				}else{
					Log.i("ECASH", "【LOG】:begin");
					byte[] tlogbyte = null;
					int lognum = LOG.size();
					for(int i=0;i<lognum;i++){
						tlogbyte = LOG.get(i);
						if(tlogbyte != null){
							Log.i("ECASH", "【LOG】:"+Utils.toHexString(tlogbyte, 0,tlogbyte.length));
						}else{
							Log.i("ECASH", "【LOG】:err-"+i);
							break;
						}
					}
					Log.i("ECASH", "【LOG】:done");
				}
				


				/*--------------------------------------------------------------*/
				// build result string
				/*--------------------------------------------------------------*/
				final EcashCard ret = new EcashCard(tag, res);
				ret.parseInfo(INFO);
				Log.i("ECASH", "【parseInfo】:done");
				
				ret.parseBalance(CASH);
				Log.i("ECASH", "【parseBalance】:done");

				ret.parseLog(logdol,LOG);
				Log.i("ECASH", "【parseLog】:done");

				ret.parseData("ATC",ATC);
				Log.i("ECASH", "【parseData】:done");


				return ret;
			}
		}

		return null;
	}
	
	public String getBalance(Iso7816.Tag tag, Resources res) {
		Log.i("ECASH", "【getBalance】:start");
		String tbalance = null;
		byte[] tbyte = null;
		int toff = 0;
		short fcioff = 0;
		
		/*--------------------------------------------------------------*/
		// select PPSE (2PAY.SYS.DDF01)
		/*--------------------------------------------------------------*/
		Iso7816.Response ppseres = tag.selectByName(PPSE);
		Log.i("ECASH", "【select PPSE】:"+ppseres.toString());

		if (ppseres.isOkey()) {
			tbyte = ppseres.getBytes();
			toff = 0;
			byte[] DFAID = null;
			
			if((byte)0x6F == tbyte[toff++]){
				if((byte)0x81 == tbyte[toff++]){
					toff++;
				}
				short FCIoff = Utils.findValueOffByTag((short) 0xA5, 
						tbyte,(short)toff,(short)tbyte[toff-1]);
				if(FCIoff < 0)
					return  null;
				
				short FCIdefoff = Utils.findValueOffByTag((short) 0xBF0C, 
						tbyte,(short)FCIoff,(short)tbyte[FCIoff-1]);
				if(FCIoff < 0)
					return  null;
				
				short Contentoff = Utils.findValueOffByTag((short) 0x61, 
						tbyte,(short)FCIdefoff,(short)tbyte[FCIdefoff-1]);
				if(Contentoff < 0)
					return  null;
				
				short DFAIDoff = Utils.findValueOffByTag((short) 0x4F, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(DFAIDoff < 0)
					return  null;
				
				short APPlaboff = Utils.findValueOffByTag((short) 0x50, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(APPlaboff < 0)
					return  null;
				
				DFAID = new byte[tbyte[DFAIDoff - 1]];
				System.arraycopy(tbyte, DFAIDoff, DFAID, 0, DFAID.length);
				
			}else{
				return null;
			}
			Log.i("ECASH", "【AID】:" + Utils.toHexString(DFAID, 0, DFAID.length));


			Iso7816.Response CASH;

			/*--------------------------------------------------------------*/
			// select Main Application
			/*--------------------------------------------------------------*/
			Iso7816.Response selectdffci = tag.selectByName(DFAID);
			Log.i("ECASH", "【select Main Application】:" + selectdffci.toString());

			if (selectdffci.isOkey()) {
				
			}
			
			/*--------------------------------------------------------------*/
			// read balance
			/*--------------------------------------------------------------*/
			CASH = tag.sendCmd("80CA9F7900");
			Log.i("ECASH", "【BLANCE】:" + CASH.toString());
			int n = Utils.BCDtoInt(CASH.getBytes(), 5, 4);
			tbalance = Utils.toAmountString(n / 100.0f);
		}
		return tbalance;

	}
	
	/**
	 * 解析PBOC卡牌你信息
	 * @param data
	 * @param dec
	 * @param bigEndian
	 */
	protected void parseInfo(Iso7816.Response data) {
		if (!data.isOkey() || data.size() < 30) {
			serl = version = date = count = null;
			return;
		}

		final byte[] d = data.getBytes();
		short toff = 0;
		short slen = 0;
		Log.i("ECASH", "【f_parseInfo_d】:"+Utils.toHexString(d, 0,d.length));
		if((byte)0x70 == (byte)d[0]){
			if((byte)0x81 == (byte)d[1]){
				slen = (short)((short)0x00FF & (short)d[2]);
				toff = 3;
			}else{
				slen = (short)((short)0x00FF & (short)d[1]);
				toff =2;
			}
			Log.i("ECASH", "【f_parseInfo_slen】:"+slen);
			Log.i("ECASH", "【f_parseInfo_toff】:"+toff);

			// PAN
			short serloff = Utils.findValueOffByTag((short) 0x5A, d,(short)toff,slen);
			Log.i("ECASH", "【f_parseInfo_serloff】:"+serloff);

			if(serloff > 0){
				serl = Utils.toHexString(d, serloff, d[serloff-1]);
			}
			if(("F").equals(serl.substring(serl.length() - 1))){
				serl = 	serl.substring(0,serl.length() - 1);
			}
		}else{
			serl ="**ERR**";
		}
	
		version = null;
		date = null;
		count = null;
	}
	
	
	protected void parseBalance(Iso7816.Response data) {
		if (!data.isOkey() || data.size() < 4) {
			cash = null;
			return;
		} 

		int n = Utils.BCDtoInt(data.getBytes(), 5, 4);
		cash = Utils.toAmountString(n / 100.0f);
	}
	

	
	protected void parseLog(byte[] logDOL,ArrayList<byte[]>... logs) {
		final StringBuilder r = new StringBuilder();
		
		int lognum = 0;// 有效日志数
		int logdateoffset = 0;// 交易日期在日志记录中的偏移
		int logtypeoffset = 0;// 交易类型在日志记录中的偏移
		int logtimeoffset = 0;// 交易时间在日志记录中的偏移
		int logmoneyoffset = 0;// 授权金额在日志记录中的偏移
		int lognameoffset = 0;// 商户名称在日志记录中的偏移
		// 查找各个tag在日志记录中的位置
		int toffset = 3;
		//tag偏移 
		int tagoff = 0;
		short tagTemp = 0;
		// 获取 交易日期 和交易时间在记录中的偏移，其他偏移也一并获取
		while (toffset < (short) (logDOL[2] + 3)) {
			// tag
			tagTemp = (short) (logDOL[toffset] & 0x00FF);
			if ((short) (tagTemp & 0x001F) == 0x001F) {
				toffset++;
				tagTemp <<= 8;
				tagTemp |= (short) (logDOL[toffset] & 0x00FF);
			}
			toffset++;

			// length
			if (logDOL[toffset] == (byte) 0x81) {
				toffset++;
			}
			// value==空

			if (tagTemp == (short) 0x9C) {
				logtypeoffset = tagoff;// 交易类型偏移
			} else if (tagTemp == (short) 0x9A) {
				logdateoffset = tagoff;// 交易日期偏移
			} else if (tagTemp == (short) 0x9F21) {
				logtimeoffset = tagoff;// 交易时间偏移
			} else if (tagTemp == (short) 0x9F02) {
				logmoneyoffset = tagoff;// 授权金额偏移
			} else if (tagTemp == (short) 0x9F4E) {
				lognameoffset = tagoff;// 商户名称偏移
			}
			tagoff += logDOL[toffset];
			toffset++;
		}

		for (final ArrayList<byte[]> log : logs) {
			if (log == null)
				continue;

			if (r.length() > 0)
				r.append("<br />--------------");

			for (final byte[] v : log) {
				final int cash = Utils.BCDtoInt(v, logmoneyoffset+2, 4);
				if (cash > 0) {
					r.append("<br />").append(
							String.format("20%02X.%02X.%02X,%02X:%02X:%02X,",
									v[logdateoffset], v[logdateoffset+1], v[logdateoffset+2], 
									v[logtimeoffset], v[logtimeoffset+1],v[logtimeoffset+2]));

					final char t = (v[logtypeoffset] == (byte)0x60 || v[logtypeoffset] == (byte)0x63) ? '+'
							: '-';

					r.append(t).append(Utils.toAmountString(cash / 100.0f));

//					final int over = Util.toInt(v, 2, 3);
//					if (over > 0)
//						r.append(" [o:")
//								.append(Util.toAmountString(over / 100.0f))
//								.append(']');
//
//					r.append(" [").append(Util.toHexString(v, 10, 6))
//							.append(']');
				}
			}
		}

		this.log = r.toString();
	}
	
	protected static ArrayList<byte[]> readLog(Iso7816.Tag tag, int sfi) {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		
		for (int i = 1; i <= MAX_LOG; ++i) {
			final Iso7816.Response tres = tag.readRecord(sfi, i);
			if(tres.isOkey()){
				ret.add(tres.getBytes());
			}else
				break;
		}
		return ret;
	}
}

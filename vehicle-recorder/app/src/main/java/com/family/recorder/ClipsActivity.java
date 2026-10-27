package com.family.recorder;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class ClipsActivity extends Activity {
    private static final int PICK_EXPORT=42;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<File> all=new ArrayList<>(),shown=new ArrayList<>();
    private final LinkedHashSet<File> selected=new LinkedHashSet<>();
    private TextView summary,empty,pathLabel,heading;
    private ProgressBar capacity;
    private BaseAdapter adapter;
    private int filter;
    private boolean selecting,busy,destroyed;
    private volatile boolean cancelRequested;
    private File sourceRoot;
    private Button multi;
    private HorizontalScrollView toolbar;
    private final Button[] tabs=new Button[4];
    private List<File> pendingExport;
    private File pendingRoot;
    private Future<?> operation;
    private ProgressDialog progress;
    private long lastProgress;
    @Override public void onCreate(Bundle b){super.onCreate(b);sourceRoot=Config.directory(this);
        setRequestedOrientation("portrait".equals(Config.prefs(this).getString("layout_mode","landscape"))?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);Ui.fullscreen(this);
        LinearLayout root=Ui.column(this);root.setPadding(d(14),d(8),d(14),d(8));root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,d(38)));
        heading=Ui.heading(this,"录像库");heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        multi=headerButton(header,"多选",()->{if(available()){selecting=!selecting;selected.clear();updateSelection();}});
        headerButton(header,"目录",this::chooseSource);headerButton(header,"返回",()->{if(available())finish();});
        summary=Ui.text(this,"正在读取录像…",11,Ui.MUTED);root.addView(summary);
        pathLabel=Ui.text(this,"",10,Ui.MUTED);pathLabel.setSingleLine(true);pathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);root.addView(pathLabel);
        capacity=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);capacity.setMax(1000);root.addView(capacity,new LinearLayout.LayoutParams(-1,d(4)));
        LinearLayout filters=new LinearLayout(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,d(34));fp.setMargins(0,d(5),0,d(5));root.addView(filters,fp);
        String[] names={"全部","已保护","前摄像头","后摄像头"};for(int i=0;i<4;i++){final int index=i;tabs[i]=Ui.button(this,names[i],i==0?Ui.GREEN:Ui.MUTED,()->{if(available()){filter=index;selected.clear();applyFilter();}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,d(5),0);filters.addView(tabs[i],p);}
        toolbar=new HorizontalScrollView(this);toolbar.setHorizontalScrollBarEnabled(true);toolbar.setVisibility(View.GONE);root.addView(toolbar,new LinearLayout.LayoutParams(-1,d(42)));LinearLayout tools=new LinearLayout(this);toolbar.addView(tools);
        tool(tools,"全选当前",()->{if(available()){selected.addAll(shown);updateSelection();}});tool(tools,"清空",()->{if(available()){selected.clear();updateSelection();}});tool(tools,"导出",()->export(choices()));tool(tools,"分享",()->share(choices()));tool(tools,"删除",()->delete(choices()));
        FrameLayout body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.BG));list.setDividerHeight(d(6));body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.text(this,"暂无已完成的录像\n当前片段保存后显示；可点“目录”查看历史路径",13,Ui.MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        adapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int position,View recycled,ViewGroup parent){
                File f=shown.get(position);LinearLayout card;Row holder;
                if(recycled==null){card=new LinearLayout(ClipsActivity.this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(d(10),d(8),d(10),d(8));card.setBackground(Ui.shape(Ui.CARD,d(12)));holder=new Row();
                    holder.check=new CheckBox(ClipsActivity.this);holder.check.setClickable(false);holder.check.setFocusable(false);holder.check.setContentDescription("选择录像");card.addView(holder.check,new LinearLayout.LayoutParams(d(36),d(40)));
                    holder.icon=Ui.text(ClipsActivity.this,"",16,Ui.GREEN);holder.icon.setGravity(Gravity.CENTER);holder.icon.setBackground(Ui.shape(0xFF223C4A,d(9)));card.addView(holder.icon,new LinearLayout.LayoutParams(d(36),d(36)));
                    LinearLayout detail=Ui.column(ClipsActivity.this);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.setMargins(d(10),0,d(8),0);card.addView(detail,dp);holder.title=Ui.text(ClipsActivity.this,"",13,Ui.TEXT);holder.title.setMaxLines(2);holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.addView(holder.title);holder.detail=Ui.text(ClipsActivity.this,"",10,Ui.MUTED);detail.addView(holder.detail);holder.state=Ui.text(ClipsActivity.this,"",11,Ui.MUTED);card.addView(holder.state);card.setTag(holder);
                }else{card=(LinearLayout)recycled;holder=(Row)card.getTag();}
                holder.check.setVisibility(selecting?View.VISIBLE:View.GONE);holder.check.setChecked(selected.contains(f));holder.icon.setText(front(f)?"前":"后");holder.title.setText(f.getName());holder.detail.setText(String.format(Locale.CHINA,"%.1f MB  ·  %s",f.length()/1048576.0,front(f)?"前摄像头":"后摄像头"));boolean locked=LoopStore.locked(f);holder.state.setText(locked?"◇ 保护":"›");holder.state.setTextColor(locked?Ui.GREEN:Ui.MUTED);return card;
            }
        };list.setAdapter(adapter);list.setOnItemClickListener((p,v,n,id)->{if(!available())return;File f=shown.get(n);if(selecting)toggle(f);else actions(f);});list.setOnItemLongClickListener((p,v,n,id)->{if(available()){selecting=true;toggle(shown.get(n));}return true;});
        if(b!=null){String saved=b.getString("source");if(saved!=null)sourceRoot=new File(saved);filter=b.getInt("filter",0);selecting=b.getBoolean("selecting",false);ArrayList<String> selection=b.getStringArrayList("selected");if(selection!=null)for(String p:selection)selected.add(new File(p));ArrayList<String> pending=b.getStringArrayList("pending_export");String origin=b.getString("pending_root");if(pending!=null&&origin!=null){pendingExport=new ArrayList<>();for(String p:pending)pendingExport.add(new File(p));pendingRoot=new File(origin);}}
    }
    private static final class Row{CheckBox check;TextView icon,title,detail,state;}
    private int d(int value){return Ui.dp(this,value);}
    private Button headerButton(LinearLayout row,String title,Runnable action){Button b=Ui.button(this,title,Ui.GREEN,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(62),d(32));p.setMargins(d(5),0,0,0);row.addView(b,p);return b;}
    private void tool(LinearLayout row,String title,Runnable action){Button b=Ui.button(this,title,title.equals("删除")?Ui.RED:Ui.GREEN,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(76),d(34));p.setMargins(0,0,d(6),0);row.addView(b,p);}
    private boolean available(){if(busy){Ui.toast(this,"正在处理，请稍候或取消当前导出");return false;}return true;}
    private static boolean front(File f){return f.getName().startsWith("前摄像头_")||f.getName().matches("VR_[0-9]+_F\\.mp4");}
    private void toggle(File f){if(!selected.remove(f))selected.add(f);updateSelection();}
    private void updateSelection(){multi.setText(selecting?"完成":"多选");heading.setText(selecting?"已选 "+selected.size():"录像库");toolbar.setVisibility(selecting?View.VISIBLE:View.GONE);if(adapter!=null)adapter.notifyDataSetChanged();}
    private List<File> choices(){return new ArrayList<>(selected);}
    @Override protected void onResume(){super.onResume();Ui.fullscreen(this);if(!busy)reload();}
    private void reload(){if(destroyed)return;final File dir=sourceRoot;final long quota=Config.quota(this);io.execute(()->{File[] files=LoopStore.files(dir);long used=0;ArrayList<File> clips=new ArrayList<>();for(File f:files){used+=f.length();if(f.getName().endsWith(".mp4"))clips.add(f);}Collections.reverse(clips);final long bytes=used;runOnUiThread(()->{if(destroyed||!sourceRoot.equals(dir))return;all.clear();all.addAll(clips);selected.retainAll(clips);summary.setText(String.format(Locale.CHINA,"%d 个片段  ·  视频 %.0f MB  ·  当前录像配额 %.0f MB",clips.size(),bytes/1048576.0,quota/1048576.0));pathLabel.setText(dir.getAbsolutePath());capacity.setProgress((int)Math.min(1000,bytes*1000/Math.max(1,quota)));applyFilter();});});}
    private void applyFilter(){shown.clear();for(File f:all)if(filter==0||(filter==1&&LoopStore.locked(f))||(filter==2&&front(f))||(filter==3&&!front(f)))shown.add(f);for(int i=0;i<4;i++)tabs[i].setTextColor(i==filter?Ui.GREEN:Ui.MUTED);updateSelection();}
    private void chooseSource(){if(!available())return;LinkedHashSet<String> paths=new LinkedHashSet<>();paths.add(Config.directory(this).getAbsolutePath());paths.add(Config.defaultPath(this));for(String s:Config.prefs(this).getString("history","").split("\n"))if(!s.isEmpty())paths.add(s);String[] values=paths.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("选择浏览目录（不改变录像保存位置）").setItems(values,(dialog,n)->{sourceRoot=new File(values[n]);selected.clear();selecting=false;reload();}).show();}
    private void actions(File f){new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{"播放录像",LoopStore.locked(f)?"解除保护":"保护此片段","导出录像","分享录像","删除录像","进入多选"},(dialog,item)->{
        if(item==0){try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(ClipProvider.uri(f),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){Ui.toast(this,"请安装支持 MP4 的播放器");}}
        if(item==1){try{synchronized(LoopStore.class){if(!f.exists())throw new IOException("录像已被循环清理");File marker=new File(f.getPath()+".lock");if(marker.exists()){if(!marker.delete())throw new IOException("无法解除保护");}else if(!marker.createNewFile())throw new IOException("无法保护");}reload();}catch(IOException e){Ui.toast(this,e.getMessage());}}
        if(item==2)export(Collections.singletonList(f));if(item==3)share(Collections.singletonList(f));if(item==4)delete(Collections.singletonList(f));if(item==5){selecting=true;selected.add(f);updateSelection();}
    }).show();}
    private void delete(List<File> files){if(!available())return;if(files.isEmpty()){Ui.toast(this,"请先选择录像");return;}final File root=sourceRoot;final ArrayList<File> snapshot=new ArrayList<>(files);
        new AlertDialog.Builder(this).setTitle("删除 "+snapshot.size()+" 个录像？").setMessage("受保护录像会自动跳过。删除不可撤销，建议先导出重要录像。").setNegativeButton("取消",null).setPositiveButton("删除未保护录像",(dialog,n)->{
            begin("批量删除",snapshot.size(),false);operation=io.submit(()->{ArchiveFiles.DeleteResult result=ArchiveFiles.delete(root,snapshot,this::updateProgress);finishOperation("删除结果","已删除 "+result.deleted+" 个\n跳过保护 "+result.protectedFiles+" 个\n已不存在 "+result.missing+" 个\n失败或不支持 "+(result.failed+result.rejected)+" 个"+(result.cancelled>0?"\n已取消 "+result.cancelled+" 个":""));});
        }).show();
    }
    private void share(List<File> files){if(!available())return;if(files.size()>100){Ui.toast(this,"一次最多分享 100 个，请分批或使用导出");return;}if(files.isEmpty()){Ui.toast(this,"请先选择录像");return;}ArrayList<Uri> uris=new ArrayList<>();for(File f:files)if(f.isFile())uris.add(ClipProvider.uri(f));if(uris.isEmpty()){Ui.toast(this,"所选录像已不存在");reload();return;}
        Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType("video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData data=ClipData.newUri(getContentResolver(),"所选录像",uris.get(0));for(int i=1;i<uris.size();i++)data.addItem(new ClipData.Item(uris.get(i)));send.setClipData(data);
        try{startActivity(Intent.createChooser(send,"分享录像（重要录像建议先保护或导出）"));}catch(ActivityNotFoundException e){Ui.toast(this,"没有可接收视频的应用，请使用导出保存");}
    }
    private void export(List<File> files){if(!available())return;if(files.isEmpty()){Ui.toast(this,"请先选择录像");return;}pendingExport=new ArrayList<>(files);pendingRoot=sourceRoot;
        new AlertDialog.Builder(this).setTitle("导出 "+files.size()+" 个录像（复制，保留原件）").setItems(new String[]{"选择系统目录 / SD 卡","输入本地导出路径"},(dialog,n)->{if(n==0){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION),PICK_EXPORT);}catch(ActivityNotFoundException e){Ui.toast(this,"系统目录选择器不可用，请输入本地路径");localPath();}}else localPath();}).setNegativeButton("取消",(dialog,n)->clearPending()).setOnCancelListener(dialog->clearPending()).show();
    }
    private void localPath(){EditText path=new EditText(this);path.setSingleLine(true);path.setText(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),"VehicleRecorderExports").getAbsolutePath());new AlertDialog.Builder(this).setTitle("本地导出父目录").setMessage("会新建独立子目录，不覆盖原文件。外置卡受限目录请使用系统目录选择器。").setView(path).setPositiveButton("开始导出",(dialog,n)->{File destination=new File(path.getText().toString().trim());if(!destination.isAbsolute()){Ui.toast(this,"请输入绝对路径");clearPending();return;}runExport(null,destination);}).setNegativeButton("取消",(dialog,n)->clearPending()).setOnCancelListener(dialog->clearPending()).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_EXPORT){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null)runExport(data.getData(),null);else{if(result==RESULT_OK)Ui.toast(this,"导出选择已失效，请重新选择录像");clearPending();}}}
    private void clearPending(){pendingExport=null;pendingRoot=null;}
    private void runExport(Uri tree,File local){if(pendingExport==null)return;final ArrayList<File> files=new ArrayList<>(pendingExport);final File root=pendingRoot;clearPending();final String folder="录像导出_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.CHINA).format(new Date());begin("导出录像",files.size(),true);
        operation=io.submit(()->{
            int completed=0,failed=0,processed=0;String firstError=null;
            try{
                if(cancelRequested||Thread.currentThread().isInterrupted())throw new InterruptedIOException("导出已取消");
                File localFolder=null;Uri documentFolder=null;
                if(tree!=null){Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));documentFolder=DocumentsContract.createDocument(getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,folder);if(documentFolder==null)throw new IOException("无法创建导出子目录");}
                else localFolder=ArchiveFiles.newDirectory(local,folder);
                for(File f:files){if(cancelRequested||Thread.currentThread().isInterrupted())break;
                    try{if(tree!=null)copyDocument(root,f,documentFolder);else ArchiveFiles.copyToDirectory(root,f,localFolder,()->cancelRequested);completed++;}catch(IOException|RuntimeException e){if(cancelRequested||Thread.currentThread().isInterrupted())break;failed++;if(firstError==null)firstError=e.getMessage();}
                    processed++;updateProgress(processed,files.size());
                }
            }catch(IOException|RuntimeException e){if(!cancelRequested&&!Thread.currentThread().isInterrupted()){firstError=e.getMessage();failed=files.size();}}
            int cancelled=Math.max(0,files.size()-completed-failed);finishOperation("导出结果","已复制 "+completed+" / "+files.size()+" 个\n失败 "+failed+" 个"+(cancelled>0?"\n未处理 "+cancelled+" 个":"")+"\n子目录："+folder+"\n原录像保留"+(firstError==null?"":"\n原因："+firstError));
        });
    }
    private void copyDocument(File root,File file,Uri directory) throws IOException {
        Uri destination=null;boolean saved=false;
        try(ArchiveFiles.Source source=ArchiveFiles.open(root,file)){
            destination=DocumentsContract.createDocument(getContentResolver(),directory,"video/mp4",source.file.getName());if(destination==null)throw new IOException("无法创建目标录像");
            OutputStream output=getContentResolver().openOutputStream(destination,"w");if(output==null)throw new IOException("无法写入目标目录");try(OutputStream out=output){ArchiveFiles.copy(source,out,()->cancelRequested);}saved=true;
        }finally{if(!saved&&destination!=null)try{DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}}
    }
    private void begin(String title,int total,boolean cancel){busy=true;cancelRequested=false;lastProgress=0;progress=new ProgressDialog(this);progress.setTitle(title);progress.setMessage("正在处理，请稍候…");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(total);progress.setCancelable(false);if(cancel)progress.setButton(DialogInterface.BUTTON_NEGATIVE,"取消导出",(DialogInterface.OnClickListener)null);progress.show();if(cancel)progress.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v->{cancelRequested=true;progress.setMessage("正在取消，已完成的副本会保留…");progress.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);});}
    private void updateProgress(int done,int total){long now=SystemClock.elapsedRealtime();if(done!=total&&now-lastProgress<150)return;lastProgress=now;runOnUiThread(()->{if(!destroyed&&progress!=null){progress.setProgress(done);progress.setMessage("已处理 "+done+" / "+total+" 个");}});}
    private void finishOperation(String title,String message){runOnUiThread(()->{if(destroyed)return;busy=false;operation=null;if(progress!=null){progress.dismiss();progress=null;}selected.clear();reload();new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();});}
    @Override public void onBackPressed(){if(busy){Ui.toast(this,"请等待处理结束，或点击取消导出");return;}if(selecting){selecting=false;selected.clear();updateSelection();}else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("source",sourceRoot.getAbsolutePath());out.putInt("filter",filter);out.putBoolean("selecting",selecting);ArrayList<String> selection=new ArrayList<>();for(File f:selected)selection.add(f.getAbsolutePath());out.putStringArrayList("selected",selection);if(pendingExport!=null&&pendingRoot!=null){ArrayList<String> pending=new ArrayList<>();for(File f:pendingExport)pending.add(f.getAbsolutePath());out.putStringArrayList("pending_export",pending);out.putString("pending_root",pendingRoot.getAbsolutePath());}super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){destroyed=true;cancelRequested=true;if(operation!=null)operation.cancel(true);if(progress!=null){progress.dismiss();progress=null;}io.shutdownNow();super.onDestroy();}
}

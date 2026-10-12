(()=>{
if(document.getElementById('harness-mobile-style'))return;
const style=document.createElement('style');style.id='harness-mobile-style';
style.textContent=`
@media(max-width:700px){
 html,body{overscroll-behavior:none;-webkit-text-size-adjust:100%}
 .pI_x6G_frame{grid-template-columns:0 minmax(0,1fr) 0!important}
 .pI_x6G_frame[data-sidebar-collapsed] .pI_x6G_sidebarCol{display:none!important}
 .pI_x6G_frame:not([data-sidebar-collapsed]) .pI_x6G_sidebarCol{display:block;position:absolute;inset:0 auto 0 0;width:min(86vw,320px);z-index:30;box-shadow:24px 0 80px #0008;border-right:1px solid #ffffff18}
 .pI_x6G_centerCol{grid-column:2!important;grid-row:1!important}
 .pI_x6G_rightbarCol,.pI_x6G_handle{display:none!important}
 .pI_x6G_frame:not([data-rightbar-collapsed]) .pI_x6G_rightbarCol{display:block!important;position:absolute;inset:0;width:100%;z-index:35;background:var(--dsw-alias-bg-base)}
 .pI_x6G_rightbarCol>*{max-width:100vw!important}
 .VOzbGW_overlay{padding:0!important}
 .VOzbGW_panel{width:100vw!important;height:100dvh!important;max-width:none!important;max-height:none!important;border-radius:0!important;display:flex!important;flex-direction:column!important}
 .VOzbGW_nav{width:100%!important;border-right:0!important;border-bottom:1px solid #ffffff18;flex:none!important;padding:12px 16px!important;box-sizing:border-box}
 .VOzbGW_navTitle{display:none}
 .VOzbGW_navList{display:flex!important;flex-direction:row!important;overflow-x:auto;gap:6px;scrollbar-width:none}
 .VOzbGW_navCell{flex:0 0 auto!important;width:auto!important;min-height:42px!important;padding:0 14px!important;border-radius:12px!important}
 .VOzbGW_content{min-width:0!important;min-height:0!important;flex:1!important;padding:18px 16px!important;overflow-y:auto!important;overflow-x:hidden!important}
 .VOzbGW_header{padding-bottom:12px!important}
 .VOzbGW_options{padding:0!important;min-width:0!important}
 .zGbnIq_section{max-width:100%!important;gap:18px!important}
 .zGbnIq_input{height:44px!important;font-size:16px!important;border-radius:10px!important}
 .zGbnIq_field{min-width:0!important}
 .zGbnIq_editor>.zGbnIq_field:has(input[aria-label="Provider ID"]){display:none!important}
 .zGbnIq_editor>.zGbnIq_field:has(input[aria-label="Provider ID"])+.zGbnIq_advancedHint{display:none!important}
 .zGbnIq_addModes{min-width:0!important;max-width:100%!important}
 .zGbnIq_addModes>div{max-width:100%!important;overflow-x:auto!important;scrollbar-width:none}
 .zGbnIq_modelRow{grid-template-columns:minmax(0,1fr) 44px 44px!important;gap:8px!important}
 .zGbnIq_modelRow>input:first-child{grid-column:auto!important}
 .zGbnIq_modelRow>input[aria-label^="Display name "]{display:none!important}
 .zGbnIq_modelAdvanced{grid-template-columns:1fr!important}
 .zGbnIq_iconButton{width:44px!important;height:44px!important}
 .zGbnIq_addCard,.zGbnIq_setupCard{padding:16px!important;border:1px solid #ffffff14!important;border-radius:18px!important}
 .zGbnIq_providerHeader,.zGbnIq_editorHeader{flex-wrap:wrap!important;gap:10px!important}
 .zGbnIq_addButton{min-width:0!important;min-height:46px!important}
 .zGbnIq_editorFooter{position:sticky;bottom:0;padding:12px 0!important;background:var(--dsw-alias-bg-base);z-index:1}
 .mobile-api-presets{display:flex;gap:8px;flex-wrap:wrap}
 .mobile-api-presets button{border:1px solid #5d83ff66;background:#5d83ff18;color:var(--dsw-alias-label-primary);border-radius:12px;padding:12px 18px;font:inherit;font-weight:600;min-height:44px}
 .mobile-api-note{font-size:13px;line-height:1.6;color:var(--dsw-alias-label-tertiary);margin:0}
 .Sixlwa_turnErrorRow{flex-wrap:wrap!important;gap:8px!important}.Sixlwa_turnErrorCopy{min-width:0!important;flex:1 1 100%!important}
 .Sixlwa_turnErrorMessage{overflow-wrap:anywhere!important}.Sixlwa_turnErrorCode{font-size:11px!important}
 [role=dialog]{max-width:100vw!important;box-sizing:border-box}
}
`;
document.head.appendChild(style);
})();

// A navigation gesture, not a JavaScript bridge: project HTML runs separately.
(()=>{
if(window.__harnessHtmlClickInstalled)return;
window.__harnessHtmlClickInstalled=true;
document.addEventListener('click',event=>{
 const target=event.target instanceof Element?event.target:null;
 if(!target)return;
 const row=target.closest('[data-files-entry="file"][data-files-path]');
 let path=row?.getAttribute('data-files-path');
 if(!path){
  const title=target.closest('button[title]')?.getAttribute('title');
  if(title?.startsWith('/root/')||title?.startsWith('/workspace/'))path=title;
 }
 if(!path){
  const link=target.closest('a[href]');
  const href=link?.getAttribute('href');
  if(href?.startsWith('file:///')){try{path=decodeURIComponent(new URL(href).pathname)}catch{}}
  else if(href?.startsWith('/root/')||href?.startsWith('/workspace/'))path=href;
 }
 if(!path||!/^\/(root|workspace)\/.*\.html?$/i.test(path))return;
 event.preventDefault();event.stopImmediatePropagation();
 window.location.href='harness-file://preview?path='+encodeURIComponent(path);
},true);
})();

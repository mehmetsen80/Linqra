import React from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import { Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import Highlight from '@tiptap/extension-highlight';
import TextAlign from '@tiptap/extension-text-align';
import { Table } from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableHeader from '@tiptap/extension-table-header';
import TableCell from '@tiptap/extension-table-cell';
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight';
import { common, createLowlight } from 'lowlight';
import { TextStyle } from '@tiptap/extension-text-style';
import { FontFamily } from '@tiptap/extension-font-family';
import {
    FaBold,
    FaItalic,
    FaUnderline,
    FaStrikethrough,
    FaCode,
    FaListUl,
    FaListOl,
    FaUndo,
    FaRedo,
    FaParagraph,
    FaHeading,
    FaQuoteRight,
    FaMinus,
    FaHighlighter,
    FaAlignLeft,
    FaAlignCenter,
    FaAlignRight,
    FaAlignJustify,
    FaFont,
    FaTable,
    FaPlus,
    FaTrash
} from 'react-icons/fa';
import { Button, ButtonGroup, Dropdown, DropdownButton } from 'react-bootstrap';

const lowlight = createLowlight(common);

const FontSize = Extension.create({
    name: 'fontSize',
    addOptions() {
        return {
            types: ['textStyle'],
        };
    },
    addGlobalAttributes() {
        return [
            {
                types: this.options.types,
                attributes: {
                    fontSize: {
                        default: null,
                        parseHTML: element => element.style.fontSize.replace(/['"]+/g, ''),
                        renderHTML: attributes => {
                            if (!attributes.fontSize) {
                                return {};
                            }
                            return {
                                style: `font-size: ${attributes.fontSize}`,
                            };
                        },
                    },
                },
            },
        ];
    },
    addCommands() {
        return {
            setFontSize: fontSize => ({ chain }) => {
                return chain()
                    .setMark('textStyle', { fontSize })
                    .run();
            },
            unsetFontSize: () => ({ chain }) => {
                return chain()
                    .setMark('textStyle', { fontSize: null })
                    .removeEmptyTextStyle()
                    .run();
            },
        };
    },
});

const MenuBar = ({ editor }) => {
    if (!editor) {
        return null;
    }

    const currentHeadingLevel = () => {
        for (let i = 1; i <= 6; i++) {
            if (editor.isActive('heading', { level: i })) return i;
        }
        return null;
    };

    const currentFontSize = () => {
        return editor.getAttributes('textStyle').fontSize || 'Size';
    };

    const currentFontFamily = () => {
        return editor.getAttributes('textStyle').fontFamily || 'Font';
    };

    const fonts = [
        { name: 'Inter', value: 'Inter' },
        { name: 'Arial', value: 'Arial' },
        { name: 'Courier New', value: 'Courier New' },
        { name: 'Georgia', value: 'Georgia' },
        { name: 'Times New Roman', value: 'Times New Roman' },
        { name: 'Verdana', value: 'Verdana' }
    ];

    return (
        <div className="rich-text-toolbar border-bottom p-1 bg-light d-flex flex-wrap gap-1">
            <ButtonGroup size="sm">
                <Button
                    variant={editor.isActive('bold') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleBold().run()}
                    title="Bold"
                >
                    <FaBold />
                </Button>
                <Button
                    variant={editor.isActive('italic') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleItalic().run()}
                    title="Italic"
                >
                    <FaItalic />
                </Button>
                <Button
                    variant={editor.isActive('underline') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleUnderline().run()}
                    title="Underline"
                >
                    <FaUnderline />
                </Button>
                <Button
                    variant={editor.isActive('strike') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleStrike().run()}
                    title="Strikethrough"
                >
                    <FaStrikethrough />
                </Button>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <Button
                    variant={editor.isActive('highlight') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleHighlight().run()}
                    title="Highlight"
                >
                    <FaHighlighter />
                </Button>
                <Button
                    variant={editor.isActive('code') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleCode().run()}
                    title="Inline Code"
                >
                    <FaCode />
                </Button>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <DropdownButton
                    size="sm"
                    variant={editor.isActive('heading') ? 'primary' : 'outline-secondary'}
                    title={currentHeadingLevel() ? `H${currentHeadingLevel()}` : <FaHeading />}
                    id="heading-dropdown"
                >
                    <Dropdown.Item
                        active={editor.isActive('paragraph')}
                        onClick={() => editor.chain().focus().setParagraph().run()}
                    >
                        Paragraph
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    {[1, 2, 3, 4, 5, 6].map(level => (
                        <Dropdown.Item
                            key={level}
                            active={editor.isActive('heading', { level })}
                            onClick={() => editor.chain().focus().toggleHeading({ level }).run()}
                            className={level > 3 ? 'small text-muted' : ''}
                        >
                            <span style={{ fontSize: `${1.5 - (level * 0.1)}rem`, fontWeight: 'bold' }}>H{level}</span>
                        </Dropdown.Item>
                    ))}
                </DropdownButton>

                <DropdownButton
                    size="sm"
                    variant={editor.getAttributes('textStyle').fontFamily ? 'primary' : 'outline-secondary'}
                    title={currentFontFamily()}
                    id="font-dropdown"
                    className="ms-1 font-dropdown"
                >
                    <Dropdown.Item onClick={() => editor.chain().focus().unsetFontFamily().run()}>
                        Default
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    {fonts.map(font => (
                        <Dropdown.Item
                            key={font.value}
                            active={editor.getAttributes('textStyle').fontFamily === font.value}
                            onClick={() => editor.chain().focus().setFontFamily(font.value).run()}
                            style={{ fontFamily: font.value }}
                        >
                            {font.name}
                        </Dropdown.Item>
                    ))}
                </DropdownButton>

                <DropdownButton
                    size="sm"
                    variant={editor.getAttributes('textStyle').fontSize ? 'primary' : 'outline-secondary'}
                    title={currentFontSize().replace('px', '')}
                    id="size-dropdown"
                    className="ms-1"
                >
                    <Dropdown.Item onClick={() => editor.chain().focus().unsetFontSize().run()}>
                        Default
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    {['8px', '10px', '12px', '14px', '16px', '18px', '20px', '24px', '30px', '36px'].map(size => (
                        <Dropdown.Item
                            key={size}
                            active={editor.getAttributes('textStyle').fontSize === size}
                            onClick={() => editor.chain().focus().setFontSize(size).run()}
                        >
                            {size.replace('px', '')}
                        </Dropdown.Item>
                    ))}
                </DropdownButton>

                <Button
                    className="ms-1"
                    variant={editor.isActive('blockquote') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleBlockquote().run()}
                    title="Blockquote"
                >
                    <FaQuoteRight />
                </Button>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <Button
                    variant={editor.isActive({ textAlign: 'left' }) ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().setTextAlign('left').run()}
                    title="Align Left"
                >
                    <FaAlignLeft />
                </Button>
                <Button
                    variant={editor.isActive({ textAlign: 'center' }) ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().setTextAlign('center').run()}
                    title="Align Center"
                >
                    <FaAlignCenter />
                </Button>
                <Button
                    variant={editor.isActive({ textAlign: 'right' }) ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().setTextAlign('right').run()}
                    title="Align Right"
                >
                    <FaAlignRight />
                </Button>
                <Button
                    variant={editor.isActive({ textAlign: 'justify' }) ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().setTextAlign('justify').run()}
                    title="Justify"
                >
                    <FaAlignJustify />
                </Button>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <Button
                    variant={editor.isActive('bulletList') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleBulletList().run()}
                    title="Bullet List"
                >
                    <FaListUl />
                </Button>
                <Button
                    variant={editor.isActive('orderedList') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleOrderedList().run()}
                    title="Numbered List"
                >
                    <FaListOl />
                </Button>
                <Button
                    variant={editor.isActive('codeBlock') ? 'primary' : 'outline-secondary'}
                    onClick={() => editor.chain().focus().toggleCodeBlock().run()}
                    title="Code Block"
                >
                    <FaCode />
                </Button>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <DropdownButton
                    size="sm"
                    variant="outline-secondary"
                    title={<FaTable />}
                    id="table-dropdown"
                >
                    <Dropdown.Item onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()}>
                        Insert Table (3x3)
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    <Dropdown.Item
                        disabled={!editor.isActive('table')}
                        onClick={() => editor.chain().focus().addRowAfter().run()}
                    >
                        Add Row After
                    </Dropdown.Item>
                    <Dropdown.Item
                        disabled={!editor.isActive('table')}
                        onClick={() => editor.chain().focus().addColumnAfter().run()}
                    >
                        Add Column After
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    <Dropdown.Item
                        disabled={!editor.isActive('table')}
                        className="text-danger"
                        onClick={() => editor.chain().focus().deleteTable().run()}
                    >
                        <FaTrash className="me-2" size={12} /> Delete Table
                    </Dropdown.Item>
                </DropdownButton>
            </ButtonGroup>

            <ButtonGroup size="sm">
                <Button
                    variant="outline-secondary"
                    onClick={() => editor.chain().focus().undo().run()}
                    disabled={!editor.can().undo()}
                    title="Undo"
                >
                    <FaUndo />
                </Button>
                <Button
                    variant="outline-secondary"
                    onClick={() => editor.chain().focus().redo().run()}
                    disabled={!editor.can().redo()}
                    title="Redo"
                >
                    <FaRedo />
                </Button>
            </ButtonGroup>
        </div>
    );
};

const RichTextEditor = ({ content, onChange, placeholder = 'Enter description...', minHeight = '150px' }) => {
    const editor = useEditor({
        extensions: [
            StarterKit,
            Underline,
            Highlight,
            TextStyle,
            FontFamily,
            FontSize,
            TextAlign.configure({
                types: ['heading', 'paragraph'],
            }),
            Table.configure({
                resizable: true,
            }),
            TableRow,
            TableHeader,
            TableCell,
            CodeBlockLowlight.configure({
                lowlight,
            }),
        ],
        content: content,
        onUpdate: ({ editor }) => {
            onChange(editor.getHTML());
        },
        editorProps: {
            attributes: {
                class: 'form-control rich-text-content focus:outline-none',
                style: `min-height: ${minHeight}; height: auto; border-top-left-radius: 0; border-top-right-radius: 0;`,
            },
        },
    });

    // Update content if it changes externally (e.g. when modal opens)
    React.useEffect(() => {
        if (editor && content !== editor.getHTML()) {
            editor.commands.setContent(content);
        }
    }, [content, editor]);

    return (
        <div className="rich-text-editor-container border rounded overflow-hidden">
            <MenuBar editor={editor} />
            <EditorContent editor={editor} />
            <style>{`
                .rich-text-content {
                    padding: 0.5rem 0.75rem;
                    background-color: #fff;
                }
                .rich-text-content:focus {
                    border-color: #86b7fe;
                    box-shadow: 0 0 0 0.25rem rgba(13, 110, 253, 0.25);
                    outline: 0;
                }
                .rich-text-toolbar .btn {
                    padding: 0.25rem 0.5rem;
                    line-height: 1.5;
                    border: none;
                }
                .rich-text-toolbar .dropdown-toggle {
                    line-height: 1.5;
                }
                .rich-text-toolbar .btn-outline-secondary {
                    color: #6c757d;
                }
                .rich-text-toolbar .btn-outline-secondary:hover {
                    background-color: #e9ecef;
                    color: #212529;
                }
                .font-dropdown .dropdown-menu {
                    max-height: 300px;
                    overflow-y: auto;
                }
                .ProseMirror p.is-editor-empty:first-child::before {
                    color: #adb5bd;
                    content: attr(data-placeholder);
                    float: left;
                    height: 0;
                    pointer-events: none;
                }
                .ProseMirror ul, .ProseMirror ol {
                    padding-left: 1.5rem;
                }
                .ProseMirror blockquote {
                    border-left: 3px solid #dee2e6;
                    padding-left: 1rem;
                    color: #6c757d;
                    font-style: italic;
                    margin: 0.5rem 0;
                }
                .ProseMirror code {
                    background-color: #f8f9fa;
                    color: #d63384;
                    padding: 0.1rem 0.3rem;
                    border-radius: 0.2rem;
                }
                .ProseMirror hr {
                    border-top: 2px solid #dee2e6;
                    margin: 1.5rem 0;
                }
                mark {
                    background-color: #fff3cd;
                    color: unset;
                }
                
                /* Tiptap Table Styles */
                .ProseMirror table {
                    border-collapse: collapse;
                    table-layout: fixed;
                    width: 100%;
                    margin: 1rem 0;
                    overflow: hidden;
                }
                .ProseMirror td, .ProseMirror th {
                    min-width: 1em;
                    border: 2px solid #ced4da;
                    padding: 8px 12px;
                    vertical-align: top;
                    box-sizing: border-box;
                    position: relative;
                }
                .ProseMirror th {
                    font-weight: bold;
                    text-align: left;
                    background-color: #f8f9fa;
                }
                .ProseMirror .selectedCell:after {
                    z-index: 2;
                    position: absolute;
                    content: "";
                    left: 0; right: 0; top: 0; bottom: 0;
                    background: rgba(200, 200, 255, 0.4);
                    pointer-events: none;
                }
                .ProseMirror .column-resize-handle {
                    position: absolute;
                    right: -2px;
                    top: 0;
                    bottom: -2px;
                    width: 4px;
                    background-color: #adf;
                    pointer-events: none;
                }

                /* Tiptap Code Highlights */
                .ProseMirror pre {
                    background: #282c34;
                    color: #abb2bf;
                    font-family: 'Fira Code', 'Courier New', monospace;
                    padding: 1rem;
                    border-radius: 0.5rem;
                    margin: 1rem 0;
                }
                .ProseMirror pre code {
                    color: inherit;
                    padding: 0;
                    background: none;
                    font-size: 0.875rem;
                }
                .hljs-comment, .hljs-quote { color: #5c6370; font-style: italic; }
                .hljs-doctag, .hljs-keyword, .hljs-formula { color: #c678dd; }
                .hljs-section, .hljs-name, .hljs-selector-tag, .hljs-deletion, .hljs-subst { color: #e06c75; }
                .hljs-literal { color: #56b6c2; }
                .hljs-string, .hljs-regexp, .hljs-addition, .hljs-attribute, .hljs-meta-string { color: #98c379; }
                .hljs-built_in, .hljs-class .hljs-title { color: #e6c07b; }
                .hljs-attr, .hljs-variable, .hljs-template-variable, .hljs-type, .hljs-selector-class, .hljs-selector-attr, .hljs-selector-pseudo, .hljs-number { color: #d19a66; }
                .hljs-symbol, .hljs-bullet, .hljs-link, .hljs-meta, .hljs-selector-id, .hljs-title { color: #61afef; }
                .hljs-emphasis { font-style: italic; }
                .hljs-strong { font-weight: bold; }
                .hljs-link { text-decoration: underline; }
            `}</style>
        </div>
    );
};

export default RichTextEditor;
